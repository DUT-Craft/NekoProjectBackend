package top.foxball.nekomainsite.controller

import jakarta.servlet.Filter
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import top.foxball.nekomainsite.authentication.LoginAttemptLimiter
import top.foxball.nekomainsite.authentication.SubmissionRateLimiter
import top.foxball.nekomainsite.service.AuthUserView

@SpringBootTest
class AuthControllerSecurityTest @Autowired constructor(
    private val controller: AuthController,
    private val context: WebApplicationContext,
    private val loginAttemptLimiter: LoginAttemptLimiter,
    private val submissionRateLimiter: SubmissionRateLimiter,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        loginAttemptLimiter.clear()
        submissionRateLimiter.clear()
        val securityFilter = context.getBean("springSecurityFilterChain", Filter::class.java)
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(securityFilter)
            .build()
    }

    @Test
    fun `login keeps the token in a strict http only cookie`() {
        val request = MockHttpServletRequest().apply { addHeader("User-Agent", "auth-controller-test") }

        val response = controller.login(AuthController.LoginRequest("admin", "admin12345"), request)

        assertEquals(200, response.statusCode.value())
        assertInstanceOf(AuthUserView::class.java, response.body?.data)
        val bodyText = response.body?.data.toString()
        assertFalse(bodyText.contains("token", ignoreCase = true))
        val cookie = response.headers.getFirst(HttpHeaders.SET_COOKIE)
        assertNotNull(cookie)
        assertTrue(cookie!!.contains("HttpOnly"))
        assertTrue(cookie.contains("SameSite=Strict"))
    }

    @Test
    fun `anonymous public writes are rejected before body validation`() {
        mockMvc.perform(
            post("/api/public/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `proxy appended client address cannot be bypassed with a spoofed prefix`() {
        repeat(LoginAttemptLimiter.MAX_FAILURES) { attempt ->
            mockMvc.perform(
                post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100.$attempt, 203.0.113.8")
                    .header("User-Agent", "auth-controller-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"admin","password":"wrong-password"}"""),
            ).andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            post("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.99, 203.0.113.8")
                .header("User-Agent", "auth-controller-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrong-password"}"""),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.status").value(429))
    }

    @Test
    fun `authenticated member writes return 429 after the configured limit`() {
        val cookie = adminCookie()
        repeat(5) { attempt ->
            mockMvc.perform(
                post("/api/public/feedback")
                    .cookie(cookie)
                    .header("User-Agent", TEST_USER_AGENT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"body":"限流控制器测试 $attempt"}"""),
            ).andExpect(status().isOk)
        }

        mockMvc.perform(
            post("/api/public/feedback")
                .cookie(cookie)
                .header("User-Agent", TEST_USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"第六次提交"}"""),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.status").value(429))
    }

    @Test
    fun `admin user list is protected and never exposes passwords`() {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/api/users")
                .cookie(adminCookie())
                .header("User-Agent", TEST_USER_AGENT),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].username").value("admin"))
            .andExpect(jsonPath("$.data[0].password").doesNotExist())
    }

    private fun adminCookie(): Cookie {
        val request = MockHttpServletRequest().apply { addHeader("User-Agent", TEST_USER_AGENT) }
        val response = controller.login(AuthController.LoginRequest("admin", "admin12345"), request)
        val setCookie = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE))
        val token = setCookie.substringAfter("neko_auth=").substringBefore(';')
        return Cookie("neko_auth", token)
    }

    private companion object {
        const val TEST_USER_AGENT = "auth-controller-test"
    }
}
