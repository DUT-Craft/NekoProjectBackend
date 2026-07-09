package `fun`.utf8.nekoprojectbackend.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis 白名单存储：
 * - `auth:token:{jti}`    access token 是否仍有效（存在即有效）
 * - `auth:refresh:{jti}`  刷新令牌白名单（一次性消费）
 * - `auth:user:{userId}:sessions` 该用户全部 access jti，用于踢人下线
 *
 * 使用 StringRedisTemplate，避免自定义 RedisTemplate 把 key 也 JSON 序列化。
 */
@Service
class TokenStore(
    private val redis: StringRedisTemplate,
) {

    fun saveAccess(jti: String, userId: Long, ttl: Duration) {
        redis.opsForValue().set(accessKey(jti), userId.toString(), ttl)
        redis.opsForSet().add(sessionIndexKey(userId), jti)
    }

    fun isAccessValid(jti: String): Boolean = redis.hasKey(accessKey(jti))

    fun invalidateAccess(jti: String, userId: Long) {
        redis.delete(accessKey(jti))
        redis.opsForSet().remove(sessionIndexKey(userId), jti)
    }

    /** 踢人下线：让该用户的所有 access token 失效 */
    fun invalidateAllSessions(userId: Long) {
        val jtis = redis.opsForSet().members(sessionIndexKey(userId)) ?: emptySet()
        jtis.forEach { redis.delete(accessKey(it)) }
        redis.delete(sessionIndexKey(userId))
    }

    fun saveRefresh(jti: String, userId: Long, ttl: Duration) {
        redis.opsForValue().set(refreshKey(jti), userId.toString(), ttl)
    }

    /** 一次性消费刷新令牌：原子地取出并删除；不存在返回 null */
    fun consumeRefresh(jti: String): Long? =
        redis.opsForValue().getAndDelete(refreshKey(jti))?.toLongOrNull()

    private fun accessKey(jti: String) = "auth:token:$jti"
    private fun refreshKey(jti: String) = "auth:refresh:$jti"
    private fun sessionIndexKey(userId: Long) = "auth:user:$userId:sessions"
}
