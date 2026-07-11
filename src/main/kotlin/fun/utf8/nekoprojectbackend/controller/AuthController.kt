package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.security.RefreshCookie
import `fun`.utf8.nekoprojectbackend.service.AuthService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/** 鉴权接口（/api/auth）：登录、邮箱验证登录、刷新、登出、当前用户、注册、验证码、改密码、找回密码。刷新令牌仅通过 HttpOnly Cookie 下发/携带。 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshCookie: RefreshCookie,
    private val builder: ResponseBuilder,
    private val operationLogService: OperationLogService,
) {

    @PostMapping("/login")
    fun login(@RequestBody req: AuthService.LoginRequest): ResponseEntity<Response> {
        val result = try {
            authService.login(req)
        } catch (e: Exception) {
            operationLogService.record(
                action = "LOGIN",
                operatorName = req.username,
                description = "登录失败",
                success = false,
                error = e.message,
            )
            throw e
        }
        operationLogService.record(action = "LOGIN", operatorName = req.username, description = "登录成功")

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

    /** 邮箱验证登录：邮箱 + 密码 + 邮箱验证码。 */
    @PostMapping("/login/email")
    fun loginByEmail(
        @RequestBody req: AuthService.EmailLoginRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val result = try {
            authService.loginByEmail(req, userAgent)
        } catch (e: Exception) {
            operationLogService.record(
                action = "EMAIL_LOGIN",
                operatorName = req.email,
                description = "邮箱验证登录失败",
                success = false,
                error = e.message,
            )
            throw e
        }
        operationLogService.record(action = "EMAIL_LOGIN", operatorName = req.email, description = "邮箱验证登录成功")

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

    @PostMapping("/register/manager")
    fun registerManager(
        @RequestBody req: AuthService.RegisterManagerRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val result = try {
            authService.registerManager(req, userAgent)
        } catch (e: Exception) {
            operationLogService.record(
                action = "PM_REGISTER",
                targetType = "USER",
                operatorName = req.username,
                description = "项目管理注册失败",
                success = false,
                error = e.message,
            )
            throw e
        }
        operationLogService.record(
            action = "PM_REGISTER",
            targetType = "USER",
            targetId = result.id,
            operatorName = req.username,
            description = "项目管理注册：${result.username}",
        )

        data class RegisterResult(
            val id: Long,
            val username: String,
            val role: Role,
        )

        val rs = RegisterResult(id = result.id, username = result.username, role = result.role)
        return builder.ok().data(rs).build()
    }

    /** 发送邮箱验证码：公开接口，按 scene+email(+userId) 绑定 UserAgent 存 Redis。 */
    @PostMapping("/verification-code")
    fun sendVerificationCode(
        @RequestBody req: AuthService.SendCodeRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        authService.sendVerificationCode(req, userAgent)
        return builder.ok().message("验证码已发送，请查收邮件").build()
    }

    /** 修改密码（已登录）：需旧密码 + 邮箱验证码确认。 */
    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal user: LoginUser,
        @RequestBody req: AuthService.ChangePasswordRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        authService.changePassword(user.id, req, userAgent)
        operationLogService.record(operator = user, action = "CHANGE_PASSWORD", description = "修改密码")
        return builder.ok()
            .header("Set-Cookie", refreshCookie.clearCookie())
            .message("密码已修改，请重新登录")
            .build()
    }

    /** 找回密码（匿名）：凭邮箱验证码重置密码。 */
    @PostMapping("/reset-password")
    fun resetPassword(
        @RequestBody req: AuthService.ResetPasswordRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        authService.resetPassword(req, userAgent)
        operationLogService.record(action = "RESET_PASSWORD", operatorName = req.email, description = "找回密码")
        return builder.ok().message("密码已重置，请用新密码登录").build()
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        authService.logout(user.jti, user.id)
        operationLogService.record(operator = user, action = "LOGOUT", description = "登出")

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
            val role: Role,
        )

        val rs = CurrentUser(
            id = user.id,
            username = user.username,
            role = user.role,
        )
        return builder.ok().data(rs).build()
    }
}
