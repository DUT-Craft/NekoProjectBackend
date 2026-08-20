package top.foxball.nekomainsite.authentication

import org.springframework.stereotype.Component

/**
 * 登录尝试限流器（进程内，无外部依赖）。
 *
 * 以「用户名 + 客户端 IP」为键，记录最近一次登录窗口内的失败次数；
 * 连续失败达到 [MAX_FAILURES] 次后锁定 [LOCK_DURATION_MS]，期间登录直接返回 429。
 * 登录成功会清空该键的计数与锁定。仅用于防护在线口令猜测，不替代后端认证与审计。
 */
@Component
class LoginAttemptLimiter {
    private class State(
        val failureTimes: MutableList<Long> = mutableListOf(),
        var lockedUntil: Long = 0L,
        var expiresAt: Long = 0L,
    )

    private val lock = Any()
    private val states = LinkedHashMap<String, State>(16, 0.75f, true)
    private var nextCleanupAt = 0L

    /** 该键当前是否处于锁定状态；锁定到期后自动清除并视为未锁定。 */
    fun isLocked(key: String, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        cleanupIfDue(now)
        val state = states[key] ?: return@synchronized false
        if (now < state.lockedUntil) return@synchronized true
        state.failureTimes.removeAll { it < now - FAILURE_WINDOW_MS }
        if (state.failureTimes.isEmpty()) {
            states.remove(key)
        } else {
            state.lockedUntil = 0L
            state.expiresAt = state.failureTimes.last() + FAILURE_WINDOW_MS
        }
        false
    }

    /** 记录一次失败；达到阈值后进入锁定。 */
    fun recordFailure(key: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        cleanupIfDue(now)
        if (key !in states && states.size >= MAX_TRACKED_KEYS) removeOldest()
        val state = states.getOrPut(key) { State() }
        if (now < state.lockedUntil) return@synchronized
        state.failureTimes.removeAll { it < now - FAILURE_WINDOW_MS }
        state.failureTimes.add(now)
        if (state.failureTimes.size >= MAX_FAILURES) {
            state.lockedUntil = now + LOCK_DURATION_MS
            state.expiresAt = state.lockedUntil
            state.failureTimes.clear()
        } else {
            state.lockedUntil = 0L
            state.expiresAt = now + FAILURE_WINDOW_MS
        }
    }

    /** 登录成功：清空该键的失败记录与锁定。 */
    fun recordSuccess(key: String) {
        synchronized(lock) { states.remove(key) }
    }

    /** 清空全部状态（仅测试使用）。 */
    fun clear() {
        synchronized(lock) {
            states.clear()
            nextCleanupAt = 0L
        }
    }

    internal fun trackedKeyCount(): Int = synchronized(lock) { states.size }

    private fun cleanupIfDue(now: Long) {
        if (now < nextCleanupAt) return
        states.entries.removeIf { it.value.expiresAt <= now }
        nextCleanupAt = now + CLEANUP_INTERVAL_MS
    }

    private fun removeOldest() {
        val iterator = states.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        const val FAILURE_WINDOW_MS = 10 * 60 * 1000L
        const val LOCK_DURATION_MS = 5 * 60 * 1000L
        const val MAX_TRACKED_KEYS = 4_096
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L
    }
}
