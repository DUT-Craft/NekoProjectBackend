package top.foxball.nekomainsite.authentication

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubmissionRateLimiterTest {
    private lateinit var limiter: SubmissionRateLimiter

    @BeforeEach
    fun setUp() {
        limiter = SubmissionRateLimiter()
    }

    @Test
    fun `allows up to the limit within the window`() {
        repeat(5) { assertTrue(limiter.allow("1|ideas", 5, 600_000L, now = 1_000L + it)) }
        assertFalse(limiter.allow("1|ideas", 5, 600_000L, now = 10_000L))
    }

    @Test
    fun `requests outside the window are allowed again`() {
        repeat(5) { limiter.allow("1|ideas", 5, 600_000L, now = 1_000L + it) }
        assertFalse(limiter.allow("1|ideas", 5, 600_000L, now = 100_000L))
        // 窗口滑过 600s 后旧记录失效，可以再次提交
        assertTrue(limiter.allow("1|ideas", 5, 600_000L, now = 1_000L + 600_000L + 60_000L))
    }

    @Test
    fun `different keys are independent`() {
        repeat(5) { limiter.allow("1|ideas", 5, 600_000L, now = 1_000L + it) }
        assertFalse(limiter.allow("1|ideas", 5, 600_000L, now = 10_000L))
        assertTrue(limiter.allow("1|applications", 5, 600_000L, now = 10_000L))
        assertTrue(limiter.allow("2|ideas", 5, 600_000L, now = 10_000L))
    }

    @Test
    fun `like limit uses its own threshold`() {
        repeat(30) { assertTrue(limiter.allow("1|likes", 30, 600_000L, now = 1_000L + it)) }
        assertFalse(limiter.allow("1|likes", 30, 600_000L, now = 100_000L))
    }

    @Test
    fun `clear resets all state`() {
        repeat(5) { limiter.allow("1|ideas", 5, 600_000L, now = 1_000L + it) }
        assertFalse(limiter.allow("1|ideas", 5, 600_000L, now = 10_000L))
        limiter.clear()
        assertTrue(limiter.allow("1|ideas", 5, 600_000L, now = 10_000L))
    }

    @Test
    fun `expired keys are removed during later traffic`() {
        limiter.allow("1|ideas", 5, 600_000L, now = 1_000L)

        limiter.allow("2|ideas", 5, 600_000L, now = 601_001L)

        assertEquals(1, limiter.trackedKeyCount())
    }

    @Test
    fun `tracked keys stay within the hard limit`() {
        repeat(SubmissionRateLimiter.MAX_TRACKED_KEYS + 100) { index ->
            limiter.allow("$index|ideas", 5, 600_000L, now = 1_000L)
        }

        assertTrue(limiter.trackedKeyCount() <= SubmissionRateLimiter.MAX_TRACKED_KEYS)
    }
}
