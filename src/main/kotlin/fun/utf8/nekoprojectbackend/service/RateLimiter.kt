package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.TooManyRequestsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

/** Redis-backed fixed-window limiter for login failures and anonymous writes. */
@Service
class RateLimiter(
    private val redis: StringRedisTemplate,
    @Value("\${neko.security.rate-limit.enabled:true}") private val enabled: Boolean,
) {
    fun consume(namespace: String, identity: String, limit: Int, window: Duration) {
        if (!enabled || identity.isBlank()) return
        require(limit > 0 && !window.isNegative && !window.isZero)

        val key = counterKey("request", namespace, identity)
        val count = increment(key, window)
        if (count > limit) {
            throw TooManyRequestsException(retryMessage(key))
        }
    }

    fun ensureNotLocked(namespace: String, identity: String) {
        if (!enabled || identity.isBlank()) return
        val key = lockKey(namespace, identity)
        val ttl = redis.getExpire(key)
        if (ttl != null && ttl > 0) {
            throw TooManyRequestsException("操作过于频繁，请 $ttl 秒后重试")
        }
    }

    /** Returns true when this failure reaches the threshold and creates a temporary lock. */
    fun recordFailure(
        namespace: String,
        identity: String,
        maxFailures: Int,
        window: Duration,
        lockDuration: Duration,
    ): Boolean {
        if (!enabled || identity.isBlank()) return false
        require(maxFailures > 0 && !window.isNegative && !window.isZero)
        require(!lockDuration.isNegative && !lockDuration.isZero)

        val failureKey = counterKey("failure", namespace, identity)
        if (increment(failureKey, window) < maxFailures) return false

        redis.opsForValue().set(lockKey(namespace, identity), "1", lockDuration)
        redis.delete(failureKey)
        return true
    }

    fun clearFailures(namespace: String, identity: String) {
        if (!enabled || identity.isBlank()) return
        redis.delete(counterKey("failure", namespace, identity))
    }

    private fun increment(key: String, window: Duration): Long =
        redis.execute(INCREMENT_SCRIPT, listOf(key), window.seconds.toString()) ?: 1L

    private fun retryMessage(key: String): String {
        val ttl = redis.getExpire(key)
        return if (ttl != null && ttl > 0) {
            "操作过于频繁，请 $ttl 秒后重试"
        } else {
            "操作过于频繁，请稍后重试"
        }
    }

    private fun counterKey(type: String, namespace: String, identity: String): String =
        "rate:$type:${safeNamespace(namespace)}:${digest(identity)}"

    private fun lockKey(namespace: String, identity: String): String =
        "rate:lock:${safeNamespace(namespace)}:${digest(identity)}"

    private fun safeNamespace(value: String): String = value.filter { it.isLetterOrDigit() || it in "-_" }.take(48)

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.trim().lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val INCREMENT_SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return count
                """.trimIndent(),
            )
            resultType = Long::class.java
        }
    }
}
