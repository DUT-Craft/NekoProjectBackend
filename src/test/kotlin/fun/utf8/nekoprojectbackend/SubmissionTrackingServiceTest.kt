package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.service.SubmissionTrackingService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmissionTrackingServiceTest {
    @Test
    fun `issued token matches only its own digest`() {
        val service = SubmissionTrackingService()
        val issued = service.issue()

        assertTrue(service.matches(issued.token, issued.hash))
        assertFalse(service.matches("wrong-token", issued.hash))
        assertFalse(service.matches(issued.token, null))
    }
}
