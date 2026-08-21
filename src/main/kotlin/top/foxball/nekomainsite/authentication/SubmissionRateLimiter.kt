package top.foxball.nekomainsite.authentication

import org.springframework.stereotype.Component

/**
 * 成员写入限流器（进程内，无外部依赖）。
 *
 * 以「用户 + 资源类型」为键，在滑动时间窗口内限制提交次数，
 * 防止单个账号刷申请、建议、报名、反馈或点赞淹没后台审核队列。
 * 阈值对正常使用足够宽松，仅在真实滥用时触发 429。
 */
@Component
class SubmissionRateLimiter {
    private class Window(
        val times: MutableList<Long> = mutableListOf(),
        var expiresAt: Long = 0L,
    )

    private val lock = Any()
    private val windows = LinkedHashMap<String, Window>(16, 0.75f, true)
    private var nextCleanupAt = 0L

    /** 当前键在窗口内是否允许下一次提交；允许时记录本次时间。 */
    fun allow(key: String, limit: Int, windowMs: Long, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        cleanupIfDue(now)
        if (key !in windows && windows.size >= MAX_TRACKED_KEYS) removeOldest()
        val window = windows.getOrPut(key) { Window() }
        window.times.removeAll { it < now - windowMs }
        if (window.times.size >= limit) return@synchronized false
        window.times.add(now)
        window.expiresAt = now + windowMs
        true
    }

    /** 清空全部状态（仅测试使用）。 */
    fun clear() {
        synchronized(lock) {
            windows.clear()
            nextCleanupAt = 0L
        }
    }

    internal fun trackedKeyCount(): Int = synchronized(lock) { windows.size }

    private fun cleanupIfDue(now: Long) {
        if (now < nextCleanupAt) return
        windows.entries.removeIf { it.value.expiresAt <= now }
        nextCleanupAt = now + CLEANUP_INTERVAL_MS
    }

    private fun removeOldest() {
        val iterator = windows.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    companion object {
        const val MAX_TRACKED_KEYS = 4_096
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L
    }
}
