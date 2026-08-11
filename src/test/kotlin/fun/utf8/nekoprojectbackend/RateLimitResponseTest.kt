package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.handlder.GlobalExceptionHandler
import `fun`.utf8.nekoprojectbackend.handlder.TooManyRequestsException
import `fun`.utf8.nekoprojectbackend.security.ClientRequestIdentity
import `fun`.utf8.nekoprojectbackend.security.ProjectControlRequestRateLimitFilter
import `fun`.utf8.nekoprojectbackend.service.RateLimiter
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import jakarta.servlet.FilterChain
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitResponseTest {
    @Test
    fun `business rate limit exception uses the unified 429 response`() {
        val response = GlobalExceptionHandler().onBusinessException(
            TooManyRequestsException("操作过于频繁，请稍后重试"),
        )

        assertEquals(429, response.statusCode.value())
        assertEquals(429, response.body?.status)
        assertEquals("操作过于频繁，请稍后重试", response.body?.message)
    }

    @Test
    fun `project control filter returns the unified 429 json response`() {
        val rateLimiter = mock(RateLimiter::class.java)
        doThrow(TooManyRequestsException("操作过于频繁，请 60 秒后重试"))
            .`when`(rateLimiter)
            .consume("project-control-ip", "203.0.113.10", 300, Duration.ofHours(1))

        val filter = ProjectControlRequestRateLimitFilter(
            rateLimiter,
            ClientRequestIdentity(false),
            JsonMapper.builder().build(),
        )
        val path = "/api/admin/project/object-items/42/verify"
        val request = MockHttpServletRequest("POST", path).apply {
            servletPath = path
            remoteAddr = "203.0.113.10"
        }
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(429, response.status)
        assertTrue(response.contentType.orEmpty().startsWith("application/json"))
        assertContains(response.contentAsString, "\"status\":429")
        assertContains(response.contentAsString, "\"message\":\"操作过于频繁，请 60 秒后重试\"")
        verifyNoInteractions(chain)
    }
}
