package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AuthService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 鉴权接口（/api/auth）：登录、刷新、登出、当前用户。 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val builder: ResponseBuilder,
) {

    @PostMapping("/login")
    fun login(@RequestBody req: AuthService.LoginRequest): ResponseEntity<Response> =
        builder.ok().data(authService.login(req)).build()

    @PostMapping("/refresh")
    fun refresh(@RequestBody req: AuthService.RefreshRequest): ResponseEntity<Response> =
        builder.ok().data(authService.refresh(req)).build()

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        authService.logout(user.jti, user.id)
        return builder.ok().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> =
        builder.ok().data(mapOf("id" to user.id, "username" to user.username)).build()
}
