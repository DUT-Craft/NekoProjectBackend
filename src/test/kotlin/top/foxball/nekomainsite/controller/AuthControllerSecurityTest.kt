package top.foxball.nekomainsite.controller

import jakarta.servlet.Filter
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import top.foxball.nekomainsite.service.AuthUserView

@SpringBootTest
class AuthControllerSecurityTest @Autowired constructor(
    private val controller: AuthController,
    private val context: WebApplicationContext,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
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
}
