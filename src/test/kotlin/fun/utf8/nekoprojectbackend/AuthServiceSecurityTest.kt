package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.UsernameOrPasswordErrorException
import `fun`.utf8.nekoprojectbackend.service.AuthService
import `fun`.utf8.nekoprojectbackend.service.JwtService
import `fun`.utf8.nekoprojectbackend.service.MailService
import `fun`.utf8.nekoprojectbackend.service.RateLimiter
import `fun`.utf8.nekoprojectbackend.service.TokenStore
import `fun`.utf8.nekoprojectbackend.service.UserService
import `fun`.utf8.nekoprojectbackend.service.VerificationCodeService
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertFailsWith

class AuthServiceSecurityTest {
    private val userService = mock(UserService::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val jwtService = mock(JwtService::class.java)
    private val tokenStore = mock(TokenStore::class.java)
    private val verificationCodeService = mock(VerificationCodeService::class.java)
    private val mailService = mock(MailService::class.java)
    private val rateLimiter = mock(RateLimiter::class.java)
    private val service = AuthService(
        userService,
        passwordEncoder,
        jwtService,
        tokenStore,
        verificationCodeService,
        mailService,
        rateLimiter,
        JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256"),
    )

    @Test
    fun `wrong password does not consume email login code`() {
        val user = user(id = 13, email = "email-login@example.test")
        `when`(userService.findByEmail(user.email)).thenReturn(user)
        `when`(passwordEncoder.matches("wrong-password", user.password)).thenReturn(false)

        assertFailsWith<UsernameOrPasswordErrorException> {
            service.loginByEmail(
                AuthService.EmailLoginRequest(
                    account = user.username,
                    password = "wrong-password",
                    email = user.email,
                    emailCode = "123456",
                ),
                "test-agent",
                "203.0.113.10",
            )
        }

        verifyNoInteractions(verificationCodeService)
    }

    @Test
    fun `password change verification is bound to the current user`() {
        val user = user(id = 14, email = "change-password@example.test")
        `when`(userService.findById(user.id!!)).thenReturn(user)
        `when`(passwordEncoder.matches("old-password", user.password)).thenReturn(true)
        `when`(passwordEncoder.encode("Str0ng!Pass")).thenReturn("encoded-new-password")

        service.changePassword(
            user.id!!,
            AuthService.ChangePasswordRequest(
                oldPassword = "old-password",
                newPassword = "Str0ng!Pass",
                confirmPassword = "Str0ng!Pass",
                emailCode = "123456",
            ),
            "test-agent",
        )

        verify(verificationCodeService).verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.CHANGE_PASSWORD,
                email = user.email,
                userId = user.id!!,
                userAgent = "test-agent",
            ),
            "123456",
        )
        verify(tokenStore).invalidateAllSessions(user.id!!)
        verify(mailService).sendSecurityNotice(user.email, "PASSWORD_CHANGED")
    }

    @Test
    fun `anonymous verification code requests do not enumerate email accounts`() {
        val email = "unknown@example.test"
        `when`(userService.findByEmail(email)).thenReturn(null)

        service.sendVerificationCode(
            AuthService.SendCodeRequest(
                email = email,
                scene = VerificationCodeService.Scene.RESET_PASSWORD,
            ),
            "test-agent",
        )

        verify(verificationCodeService).checkAndRecordSend(email)
        verifyNoInteractions(mailService)
    }

    @Test
    fun `logout does not revoke a refresh token owned by another user`() {
        val jwt = JwtService(JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256"))
        val issued = jwt.issueRefreshToken(99, "other-user", Role.USER.name, 600)
        `when`(jwtService.parse(issued.token)).thenReturn(jwt.parse(issued.token))

        service.logout(jti = "access-jti", userId = 14, refreshToken = issued.token)

        verify(tokenStore).invalidateAccess("access-jti", 14)
        verify(tokenStore, never()).revokeRefresh(issued.jti, 14)
    }

    @Test
    fun `oversized login password is rejected before bcrypt matching`() {
        assertFailsWith<UsernameOrPasswordErrorException> {
            service.login(
                AuthService.LoginRequest(
                    account = "member-12",
                    password = "中".repeat(25),
                ),
                "test-agent",
                "203.0.113.10",
            )
        }

        verifyNoInteractions(userService, passwordEncoder)
    }

    private fun user(id: Long, email: String) = User().apply {
        this.id = id
        this.username = "member-$id"
        this.usernameLower = this.username.lowercase()
        this.password = "encoded-password"
        this.email = email
        this.nickname = "member"
        this.status = Status.ACTIVE
        this.role = Role.USER
        this.emailVerifiedAt = LocalDateTime.now()
    }
}
