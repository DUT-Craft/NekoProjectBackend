package top.foxball.nekomainsite.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.nekomainsite.authentication.LoginTokenAuthentication
import top.foxball.nekomainsite.config.JwtProperties
import top.foxball.nekomainsite.service.AuthService
import top.foxball.nekomainsite.service.BlessingSkinAuthService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val loginTokenAuthentication: LoginTokenAuthentication,
    private val blessingSkinAuthService: BlessingSkinAuthService,
    private val jwtProperties: JwtProperties,
    private val builder: ResponseBuilder,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): ResponseEntity<ApiResponse> {
        val result = authService.login(request.username.trim(), request.password, http.getHeader("User-Agent").orEmpty())
        return authenticatedResponse(result, "登录失败")
    }

    @GetMapping("/me")
    fun me(authentication: Authentication?): ResponseEntity<ApiResponse> {
        val userId = authentication?.principal as? Long
        val user = authService.currentUser(userId)
            ?: return builder.ok().data(mapOf("authenticated" to false)).build()
        return builder.ok().data(user).build()
    }

    @PostMapping("/logout")
    fun logout(http: HttpServletRequest): ResponseEntity<ApiResponse> {
        extractToken(http)?.let(loginTokenAuthentication::deleteToken)
        return builder.ok().data(mapOf("loggedOut" to true))
            .header(HttpHeaders.SET_COOKIE, expiredCookie().toString()).build()
    }

    @GetMapping("/blessing/start")
    fun blessingStart(): ResponseEntity<ApiResponse> = builder.ok()
        .data(mapOf("authorizationUrl" to blessingSkinAuthService.authorizationUrl())).build()

    @GetMapping("/blessing/callback")
    fun blessingCallback(
        @RequestParam code: String,
        @RequestParam state: String,
        http: HttpServletRequest,
    ): ResponseEntity<ApiResponse> {
        val result = blessingSkinAuthService.callback(code, state, http.getHeader("User-Agent").orEmpty())
        return authenticatedResponse(result, "皮肤站登录失败")
    }

    private fun authenticatedResponse(
        result: LoginTokenAuthentication.LoginResult,
        failureMessage: String,
    ): ResponseEntity<ApiResponse> {
        val session = result.response ?: return builder.unauthorized().message(failureMessage).build()
        val user = authService.currentUser(session.userId) ?: run {
            loginTokenAuthentication.deleteToken(session.token)
            return builder.unauthorized().message(failureMessage).build()
        }
        return builder.ok().data(user).header(HttpHeaders.SET_COOKIE, authCookie(session.token).toString()).build()
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header?.startsWith("Bearer ", ignoreCase = true) == true) return header.substring(7).trim()
        return request.cookies?.firstOrNull { it.name == "neko_auth" }?.value
    }

    private fun authCookie(token: String): ResponseCookie = ResponseCookie.from("neko_auth", token)
        .httpOnly(true).secure(jwtProperties.cookieSecure).path("/").sameSite("Strict").maxAge(jwtProperties.ttlSeconds).build()

    private fun expiredCookie(): ResponseCookie = ResponseCookie.from("neko_auth", "")
        .httpOnly(true).secure(jwtProperties.cookieSecure).path("/").sameSite("Strict").maxAge(0).build()

    data class LoginRequest(
        @field:NotBlank @field:Size(max = 50) val username: String,
        @field:NotBlank @field:Size(max = 128) val password: String,
    )
}
