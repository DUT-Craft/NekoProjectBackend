package top.foxball.nekomainsite.authentication

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginAttemptLimiterTest {
    private lateinit var limiter: LoginAttemptLimiter

    @BeforeEach
    fun setUp() {
        limiter = LoginAttemptLimiter()
    }

    @Test
    fun `unknown key is not locked`() {
        assertFalse(limiter.isLocked("admin|127.0.0.1", now = 1_000L))
    }

    @Test
    fun `five failures lock the key`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        assertTrue(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
    }

    @Test
    fun `four failures do not lock the key`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES - 1) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        assertFalse(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
    }

    @Test
    fun `lock expires after the lock duration`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        assertTrue(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
        assertFalse(limiter.isLocked("admin|127.0.0.1", now = 10_000L + LoginAttemptLimiter.LOCK_DURATION_MS + 1))
    }

    @Test
    fun `success clears failures and lock`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        assertTrue(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
        limiter.recordSuccess("admin|127.0.0.1")
        assertFalse(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
    }

    @Test
    fun `different keys are independent`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        assertTrue(limiter.isLocked("admin|127.0.0.1", now = 10_000L))
        assertFalse(limiter.isLocked("admin|10.0.0.1", now = 10_000L))
        assertFalse(limiter.isLocked("other|127.0.0.1", now = 10_000L))
    }

    @Test
    fun `old failures outside the window do not count`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES - 1) { limiter.recordFailure("admin|127.0.0.1", now = 1_000L + it) }
        // 过期失败不累计；窗口外再失败 1 次仍不锁定
        limiter.recordFailure("admin|127.0.0.1", now = 1_000L + LoginAttemptLimiter.FAILURE_WINDOW_MS + 60_000L)
        assertFalse(limiter.isLocked("admin|127.0.0.1", now = 1_000L + LoginAttemptLimiter.FAILURE_WINDOW_MS + 120_000L))
    }

    @Test
    fun `expired keys are removed during later traffic`() {
        limiter.recordFailure("old|127.0.0.1", now = 1_000L)

        limiter.recordFailure("new|127.0.0.1", now = 1_000L + LoginAttemptLimiter.FAILURE_WINDOW_MS + 1L)

        assertEquals(1, limiter.trackedKeyCount())
    }

    @Test
    fun `tracked keys stay within the hard limit`() {
        repeat(LoginAttemptLimiter.MAX_TRACKED_KEYS + 100) { index ->
            limiter.recordFailure("user-$index|192.0.2.1", now = 1_000L)
        }

        assertTrue(limiter.trackedKeyCount() <= LoginAttemptLimiter.MAX_TRACKED_KEYS)
    }

    @Test
    fun `capacity pressure never evicts an active lock`() {
        val lockedKey = "admin|192.0.2.1"
        repeat(LoginAttemptLimiter.MAX_FAILURES) { limiter.recordFailure(lockedKey, now = 1_000L + it) }

        repeat(LoginAttemptLimiter.MAX_TRACKED_KEYS + 100) { index ->
            limiter.recordFailure("user-$index|192.0.2.1", now = 10_000L)
        }

        assertTrue(limiter.isLocked(lockedKey, now = 10_001L))
        assertTrue(limiter.trackedKeyCount() <= LoginAttemptLimiter.MAX_TRACKED_KEYS)
    }
}
