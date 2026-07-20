package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.config.NekoSecurityProperties
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

/** 鉴权接口（/api/auth）：统一 account 登录、邮箱验证登录、刷新、登出、当前用户、注册、验证码、改密码、找回密码、找回用户名。 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshCookie: RefreshCookie,
    private val builder: ResponseBuilder,
    private val operationLogService: OperationLogService,
    private val nekoSecurityProperties: NekoSecurityProperties,
) {

    @PostMapping("/login")
    fun login(
        @RequestBody req: AuthService.LoginRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val ip = clientIp(request)
        val result = try {
            authService.login(req, userAgent, ip)
        } catch (e: Exception) {
            operationLogService.record(
                action = "LOGIN",
                operatorName = req.account,
                description = "登录失败",
                success = false,
                error = e.message,
            )
            throw e
        }
        operationLogService.record(action = "LOGIN", operatorName = req.account, description = "登录成功")

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
        val ip = clientIp(request)
        val result = try {
            authService.loginByEmail(req, userAgent, ip)
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
        val userAgent = request.getHeader("User-Agent") ?: ""
        val ip = clientIp(request)
        val refreshToken = refreshCookie.read(request.cookies)
            ?: throw TokenInvalidException("刷新令牌缺失")
        val result = authService.refresh(refreshToken, userAgent, ip)

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

    /** 项目管理凭一次性邀请码注册（过渡保留，阶段四补 /register 公开注册）。 */
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

    /** 普通用户公开注册（设计 §4.1，无需邀请码）。 */
    @PostMapping("/register")
    fun register(
        @RequestBody req: AuthService.RegisterUserRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val result = try {
            authService.registerUser(req, userAgent)
        } catch (e: Exception) {
            operationLogService.record(
                action = "USER_REGISTER",
                targetType = "USER",
                operatorName = req.username,
                description = "用户注册失败",
                success = false,
                error = e.message,
            )
            throw e
        }
        operationLogService.record(
            action = "USER_REGISTER",
            targetType = "USER",
            targetId = result.id,
            operatorName = req.username,
            description = "用户注册：${result.username}",
        )

        data class RegisterResult(
            val id: Long,
            val username: String,
            val role: Role,
        )

        val rs = RegisterResult(id = result.id, username = result.username, role = result.role)
        return builder.ok().data(rs).build()
    }

    /** 已登录用户凭邀请码补授项目创建资格（设计 §4.2）。 */
    @PostMapping("/create-project-grant")
    fun createProjectGrant(
        @AuthenticationPrincipal user: LoginUser,
        @RequestBody req: AuthService.CreateProjectGrantRequest,
    ): ResponseEntity<Response> {
        authService.grantCreateProject(user.id, req)
        operationLogService.record(
            operator = user,
            action = "CREATE_PROJECT_GRANT",
            targetType = "USER",
            targetId = user.id,
            description = "凭邀请码补授项目创建资格",
        )
        return builder.ok().message("项目创建资格已授予").build()
    }

    /**
     * 发送邮箱验证码：匿名场景（注册 / 找回密码 / 找回用户名）按 email+UA 绑定；
     * 改密码场景需登录态，按当前用户 userId 绑定（忽略请求体 email）。
     */
    @PostMapping("/verification-code")
    fun sendVerificationCode(
        @AuthenticationPrincipal user: LoginUser?,
        @RequestBody req: AuthService.SendCodeRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        authService.sendVerificationCode(req, userAgent, user)
        return builder.ok().message("验证码已发送，请查收邮件").build()
    }

    /** 修改密码（已登录）：需旧密码 + 本人邮箱验证码确认 + 新密码强度校验。 */
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

    /** 找回用户名（匿名）：凭邮箱验证码把用户名发送到绑定邮箱，不向调用方回显（设计 §6.3）。 */
    @PostMapping("/username/recover")
    fun recoverUsername(
        @RequestBody req: AuthService.RecoverUsernameRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        authService.recoverUsername(req, userAgent)
        return builder.ok().message("若该邮箱已注册，用户名已发送至该邮箱").build()
    }

    /** 恢复停用账号（匿名，设计 §10 修正）：邮箱验证码 + 密码双重确认后置 ACTIVE 并签发新会话。 */
    @PostMapping("/reactivate")
    fun reactivate(
        @RequestBody req: AuthService.ReactivateRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val ip = clientIp(request)
        val result = authService.reactivateByEmail(req, userAgent, ip)
        operationLogService.record(
            action = "USER_REACTIVATE",
            operatorName = req.email,
            description = "凭邮箱验证码恢复停用账号",
        )

        data class ReactivateResult(
            val accessToken: String,
            val tokenType: String,
            val expiresIn: Long,
        )

        val rs = ReactivateResult(
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
    fun logout(
        @AuthenticationPrincipal user: LoginUser,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        authService.logout(user.jti, user.id, refreshCookie.read(request.cookies))
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

    /** 会话管理（设计 §12）：列出当前用户全部有效会话，标记当前会话。 */
    @GetMapping("/sessions")
    fun listSessions(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        val sessions = authService.listSessions(user.id).map {
            mapOf(
                "jti" to it.jti,
                "userId" to it.userId,
                "userAgent" to it.userAgent,
                "ip" to it.ip,
                "issuedAt" to it.issuedAt,
                "current" to (it.jti == user.jti),
            )
        }
        return builder.ok().data(sessions).build()
    }

    /** 登出指定设备（按 jti）。 */
    @DeleteMapping("/sessions/{jti}")
    fun invalidateSession(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable jti: String,
    ): ResponseEntity<Response> {
        authService.invalidateSession(jti, user.id)
        operationLogService.record(operator = user, action = "SESSION_INVALIDATE", description = "登出设备 $jti")
        return builder.ok().message("已登出该设备").build()
    }

    /** 退出全部设备。 */
    @DeleteMapping("/sessions")
    fun invalidateAllSessions(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        authService.invalidateAllSessions(user.id)
        operationLogService.record(operator = user, action = "SESSION_INVALIDATE_ALL", description = "退出全部设备")
        return builder.ok()
            .header("Set-Cookie", refreshCookie.clearCookie())
            .message("已退出全部设备")
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

    /**
     * 取客户端 IP：仅在部署于可信反代（neko.security.trusted-proxy=true）后才采信
     * X-Forwarded-For / X-Real-IP；否则用 remoteAddr，防客户端伪造这些头绕过 IP 维度限流（设计 §5.2 修正）。
     */
    private fun clientIp(request: HttpServletRequest): String {
        if (nekoSecurityProperties.trustedProxy) {
            request.getHeader("X-Forwarded-For")?.let {
                val first = it.substringBefore(',').trim()
                if (first.isNotEmpty()) return first
            }
            request.getHeader("X-Real-IP")?.let { if (it.isNotBlank()) return it.trim() }
        }
        return request.remoteAddr
    }
}
