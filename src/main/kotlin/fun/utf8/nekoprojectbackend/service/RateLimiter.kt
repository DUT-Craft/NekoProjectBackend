package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 限流组件（基于 Redis，设计 §5.2 / §12）：滑动失败计数 + 临时锁定。
 *
 * - [recordFailAndCheckLock]：累计失败次数，达阈值写入 lock key 并返回锁定；
 * - [ensureNotLocked]：检查是否处于锁定，锁定则抛 429；
 * - [clearFails]：成功后清零计数。
 *
 * 临时锁定存 Redis（区别于管理员封禁），不暴露具体账号是否存在。
 */
@Service
class RateLimiter(
    private val redis: StringRedisTemplate,
) {
    /** 命中锁定抛 429，message 不含账号名，仅提示剩余秒数。 */
    fun ensureNotLocked(namespace: String, identity: String) {
        val ttl = redis.getExpire(lockKey(namespace, identity))
        if (ttl != null && ttl > 0) {
            throw BusinessException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请 ${ttl}s 后重试")
        }
    }

    /**
     * 记录一次失败并判断是否触发锁定。
     * @return true 表示本次失败触发了锁定（调用方通常仍抛业务异常）。
     */
    fun recordFailAndCheckLock(
        namespace: String,
        identity: String,
        maxFail: Int,
        windowSeconds: Long,
        lockSeconds: Long,
    ): Boolean {
        val key = failKey(namespace, identity)
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds))
        }
        if (count >= maxFail) {
            redis.opsForValue().set(lockKey(namespace, identity), "1", Duration.ofSeconds(lockSeconds))
            redis.delete(key)
            return true
        }
        return false
    }

    /** 成功后清零失败计数。 */
    fun clearFails(namespace: String, identity: String) {
        redis.delete(failKey(namespace, identity))
    }

    private fun failKey(namespace: String, identity: String) = "rate:fail:$namespace:$identity"
    private fun lockKey(namespace: String, identity: String) = "rate:lock:$namespace:$identity"
}
