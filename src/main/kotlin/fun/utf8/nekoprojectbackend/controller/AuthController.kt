package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.security.RefreshCookie
import `fun`.utf8.nekoprojectbackend.service.AuthService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 鉴权接口（/api/auth）：登录、刷新、登出、当前用户。刷新令牌仅通过 HttpOnly Cookie 下发/携带。 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshCookie: RefreshCookie,
    private val builder: ResponseBuilder,
) {

    @PostMapping("/login")
    fun login(@RequestBody req: AuthService.LoginRequest): ResponseEntity<Response> {
        val result = authService.login(req)

        data class LoginResult(
            val accessToken: String,
            val tokenType: String,
            val expiresIn: Long,
        )

        val rs = LoginResult(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresIn = result.expiresIn,
        )
        return builder.ok()
            .header("Set-Cookie", refreshCookie.setCookie(result.refreshToken, result.refreshExpiresIn))
            .data(rs)
            .build()
    }

    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest): ResponseEntity<Response> {
        val refreshToken = refreshCookie.read(request.cookies)
            ?: throw TokenInvalidException("刷新令牌缺失")
        val result = authService.refresh(refreshToken)

        data class RefreshResult(
            val accessToken: String,
            val tokenType: String,
            val expiresIn: Long,
        )

        val rs = RefreshResult(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresIn = result.expiresIn,
        )
        return builder.ok()
            .header("Set-Cookie", refreshCookie.setCookie(result.refreshToken, result.refreshExpiresIn))
            .data(rs)
            .build()
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        authService.logout(user.jti, user.id)

        data class LogoutResult(
            val loggedOut: Boolean,
            val id: Long,
        )

        val rs = LogoutResult(
            loggedOut = true,
            id = user.id,
        )
        return builder.ok()
            .header("Set-Cookie", refreshCookie.clearCookie())
            .data(rs)
            .build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        data class CurrentUser(
            val id: Long,
            val username: String,
        )

        val rs = CurrentUser(
            id = user.id,
            username = user.username,
        )
        return builder.ok().data(rs).build()
    }
}
