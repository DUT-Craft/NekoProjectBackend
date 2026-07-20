package `fun`.utf8.nekoprojectbackend.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis 白名单存储：
 * - `auth:token:{jti}`    access token 是否仍有效（存在即有效）
 * - `auth:refresh:{jti}`  刷新令牌白名单（一次性消费）
 * - `auth:user:{userId}:sessions`   该用户全部 access jti，用于踢人下线
 * - `auth:user:{userId}:refreshes`  该用户全部 refresh jti，改密码 / 找回密码时一并吊销
 *
 * 使用 StringRedisTemplate，避免自定义 RedisTemplate 把 key 也 JSON 序列化。
 */
@Service
class TokenStore(
    private val redis: StringRedisTemplate,
) {

    /** 会话元数据（供 GET /api/auth/sessions 展示设备列表）。issuedAt 为 epoch 毫秒。 */
    data class SessionInfo(
        val jti: String,
        val userId: Long,
        val userAgent: String?,
        val ip: String?,
        val issuedAt: Long?,
    )

    /** access value 以 `userId|ua|ip|issuedAt` 存储，既保留踢人所需的归属，又携带会话元数据。 */
    fun saveAccess(jti: String, userId: Long, userAgent: String, ip: String, ttl: Duration) {
        val ua = userAgent.take(MAX_UA_LEN).replace("|", " ")
        val safeIp = ip.replace("|", " ")
        val value = "$userId|$ua|$safeIp|${System.currentTimeMillis()}"
        redis.opsForValue().set(accessKey(jti), value, ttl)
        redis.opsForSet().add(sessionIndexKey(userId), jti)
    }

    /** 列出用户当前全部有效会话（access 仍在白名单中的）。 */
    fun listSessions(userId: Long): List<SessionInfo> {
        val jtis = redis.opsForSet().members(sessionIndexKey(userId)) ?: emptySet()
        return jtis.mapNotNull { jti ->
            val value = redis.opsForValue().get(accessKey(jti)) ?: return@mapNotNull null
            val parts = value.split("|", limit = 4)
            SessionInfo(
                jti = jti,
                userId = parts.getOrNull(0)?.toLongOrNull() ?: userId,
                userAgent = parts.getOrNull(1)?.ifBlank { null },
                ip = parts.getOrNull(2)?.ifBlank { null },
                issuedAt = parts.getOrNull(3)?.toLongOrNull(),
            )
        }
    }

    fun isAccessValid(jti: String): Boolean = redis.hasKey(accessKey(jti))

    /**
     * 登出指定会话：先校验该 jti 确属该 userId（防止越权登出他人会话，设计 §12 修正），
     * 再删除 access key 与会话索引项。归属不符则静默 no-op（不暴露 jti 是否存在）。
     */
    fun invalidateAccess(jti: String, userId: Long) {
        val owned = redis.opsForSet().isMember(sessionIndexKey(userId), jti) == true
        if (!owned) {
            return
        }
        redis.delete(accessKey(jti))
        redis.opsForSet().remove(sessionIndexKey(userId), jti)
    }

    /**
     * 踢人下线：让该用户的所有 access 与 refresh token 同时失效。
     * 改密码 / 找回密码时调用——此前只清 access，残留的 refresh 仍可换出新令牌，使"改密码赶人"失效。
     */
    fun invalidateAllSessions(userId: Long) {
        val accessJtis = redis.opsForSet().members(sessionIndexKey(userId)) ?: emptySet()
        accessJtis.forEach { redis.delete(accessKey(it)) }
        redis.delete(sessionIndexKey(userId))

        val refreshJtis = redis.opsForSet().members(refreshIndexKey(userId)) ?: emptySet()
        refreshJtis.forEach { redis.delete(refreshKey(it)) }
        redis.delete(refreshIndexKey(userId))
    }

    fun saveRefresh(jti: String, userId: Long, ttl: Duration) {
        redis.opsForValue().set(refreshKey(jti), userId.toString(), ttl)
        // 建立用户→refresh 索引，供改密码 / 找回密码时批量吊销该用户全部刷新令牌
        redis.opsForSet().add(refreshIndexKey(userId), jti)
    }

    /** 一次性消费刷新令牌：原子地取出并删除；不存在返回 null。顺带从用户索引移除，防集合膨胀。 */
    fun consumeRefresh(jti: String): Long? {
        val userId = redis.opsForValue().getAndDelete(refreshKey(jti))?.toLongOrNull()
            ?: return null
        redis.opsForSet().remove(refreshIndexKey(userId), jti)
        return userId
    }

    /** 吊销单条刷新令牌（登出用）：幂等，jti 不存在亦无妨。同时从用户索引移除。 */
    fun revokeRefresh(jti: String, userId: Long) {
        redis.delete(refreshKey(jti))
        redis.opsForSet().remove(refreshIndexKey(userId), jti)
    }

    private fun accessKey(jti: String) = "auth:token:$jti"
    private fun refreshKey(jti: String) = "auth:refresh:$jti"
    private fun sessionIndexKey(userId: Long) = "auth:user:$userId:sessions"
    private fun refreshIndexKey(userId: Long) = "auth:user:$userId:refreshes"

    private companion object {
        const val MAX_UA_LEN = 160
    }
}
