package `fun`.utf8.nekoprojectbackend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * 项目管理邀请码：总管理生成的一次性注册码，存于 Redis。
 *
 * - key: `invite:pm:{code}`，value 固定 "1"，带 TTL（默认 7 天，可经 neko.invite.ttl-seconds 配置）；
 * - [consume] 使用 Redis 原子 getAndDelete，保证一张邀请码只能注册一次。
 *
 * 注意：采用「先消费后建号」——若注册中途失败（如用户名重复），邀请码已被消耗，需总管理重新生成。
 */
@Service
class InviteCodeService(
    private val redis: StringRedisTemplate,
    @Value("\${neko.invite.ttl-seconds:604800}") private val ttlSeconds: Long,
) {
    /** 生成一张全新的一次性邀请码并写入 Redis，返回明文码。 */
    fun generate(): String {
        val code = UUID.randomUUID().toString().replace("-", "")
        redis.opsForValue().set(key(code), MARKER, Duration.ofSeconds(ttlSeconds))
        return code
    }

    /** 原子消费：有效则删除并返回 true；不存在 / 已使用返回 false。 */
    fun consume(code: String): Boolean {
        if (code.isBlank()) return false
        return redis.opsForValue().getAndDelete(key(code)) != null
    }

    private fun key(code: String) = "$PREFIX$code"

    private companion object {
        const val PREFIX = "invite:pm:"
        const val MARKER = "1"
    }
}
