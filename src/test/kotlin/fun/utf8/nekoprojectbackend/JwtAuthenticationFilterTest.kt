package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.security.JwtAuthenticationFilter
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.JwtService
import `fun`.utf8.nekoprojectbackend.service.TokenStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {
    private val jwtService = JwtService(
        JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256"),
    )
    private val tokenStore = mock(TokenStore::class.java)
    private val filter = JwtAuthenticationFilter(jwtService, tokenStore)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `valid access token creates the expected principal`() {
        val issued = jwtService.issueAccessToken(7, "manager", Role.PROJECT_MANAGER.name, 60)
        `when`(tokenStore.isAccessValid(issued.jti)).thenReturn(true)

        val request = authenticatedRequest(issued.token)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val principal = SecurityContextHolder.getContext().authentication?.principal
        assertIs<LoginUser>(principal)
        assertEquals(7, principal.id)
        assertEquals(Role.PROJECT_MANAGER, principal.role)
    }

    @Test
    fun `refresh token cannot authenticate an api request`() {
        val issued = jwtService.issueRefreshToken(7, "manager", Role.PROJECT_MANAGER.name, 60)
        `when`(tokenStore.isAccessValid(issued.jti)).thenReturn(true)

        val request = authenticatedRequest(issued.token)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
        assertIs<TokenInvalidException>(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR))
    }

    @Test
    fun `unknown role is rejected instead of becoming a project manager`() {
        val issued = jwtService.issueAccessToken(7, "manager", "UNKNOWN_ROLE", 60)
        `when`(tokenStore.isAccessValid(issued.jti)).thenReturn(true)

        val request = authenticatedRequest(issued.token)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
        assertIs<TokenInvalidException>(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR))
    }

    private fun authenticatedRequest(token: String) = MockHttpServletRequest().apply {
        addHeader("Authorization", "Bearer $token")
    }
}
