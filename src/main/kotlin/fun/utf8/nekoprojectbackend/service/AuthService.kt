package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.*
import jakarta.transaction.Transactional
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 鉴权业务：登录（密码 / 邮箱+密码+验证码）、登出（白名单驱逐）、
 * 刷新令牌（一次性消费，刷新时重读用户以同步角色）、项目管理凭邀请码注册、
 * 修改密码（需验证码确认）、找回密码（凭验证码重置）。
 *
 * 邮箱验证码与「场景 + 用户标识 + UserAgent」绑定（见 [VerificationCodeService]）：
 * 匿名场景（注册 / 找回密码）用 email 绑定；已登录场景（改密码确认）用 userId 绑定。
 */
@Service
class AuthService(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val tokenStore: TokenStore,
    private val inviteCodeService: InviteCodeService,
    private val verificationCodeService: VerificationCodeService,
    private val mailService: MailService,
    private val rateLimiter: RateLimiter,
    private val props: JwtProperties,
) {

    data class LoginRequest(
        @field:NotBlank(message = "用户名不能为空")
        @field:Size(max = 64, message = "用户名不能超过 64 个字符")
        val username: String,
        @field:NotBlank(message = "密码不能为空")
        @field:Size(max = 72, message = "密码不能超过 72 个字符")
        val password: String,
    )
    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshExpiresIn: Long,
    )

    data class RegisterManagerRequest(
        @field:NotBlank(message = "邀请码不能为空")
        @field:Size(max = 128, message = "邀请码不能超过 128 个字符")
        val inviteCode: String,
        @field:NotBlank(message = "用户名不能为空")
        @field:Size(max = 64, message = "用户名不能超过 64 个字符")
        val username: String,
        @field:Size(min = 8, max = 72, message = "密码长度必须为 8 到 72 个字符")
        val password: String,
        @field:NotBlank(message = "邮箱不能为空")
        @field:Email(message = "邮箱格式不正确")
        @field:Size(max = 128, message = "邮箱不能超过 128 个字符")
        val email: String,
        @field:NotBlank(message = "验证码不能为空")
        @field:Size(max = 16, message = "验证码格式错误")
        val emailCode: String,
    )

    data class RegisterManagerResponse(
        val id: Long,
        val username: String,
        val role: Role,
    )

    /** 邮箱+密码登录请求：username 可为用户名或邮箱，需额外校验邮箱验证码。 */
    data class EmailLoginRequest(
        @field:NotBlank(message = "账号不能为空")
        @field:Size(max = 128, message = "账号不能超过 128 个字符")
        val account: String,
        @field:NotBlank(message = "密码不能为空")
        @field:Size(max = 72, message = "密码不能超过 72 个字符")
        val password: String,
        @field:NotBlank(message = "邮箱不能为空")
        @field:Email(message = "邮箱格式不正确")
        @field:Size(max = 128, message = "邮箱不能超过 128 个字符")
        val email: String,
        @field:NotBlank(message = "验证码不能为空")
        @field:Size(max = 16, message = "验证码格式错误")
        val emailCode: String,
    )

    data class SendCodeRequest(
        @field:NotBlank(message = "邮箱不能为空")
        @field:Email(message = "邮箱格式不正确")
        @field:Size(max = 128, message = "邮箱不能超过 128 个字符")
        val email: String,
        val scene: VerificationCodeService.Scene,
    )

    data class ChangePasswordRequest(
        @field:NotBlank(message = "旧密码不能为空")
        @field:Size(max = 72, message = "旧密码不能超过 72 个字符")
        val oldPassword: String,
        @field:Size(min = 8, max = 72, message = "新密码长度必须为 8 到 72 个字符")
        val newPassword: String,
        @field:NotBlank(message = "邮箱不能为空")
        @field:Email(message = "邮箱格式不正确")
        @field:Size(max = 128, message = "邮箱不能超过 128 个字符")
        val email: String,
        @field:NotBlank(message = "验证码不能为空")
        @field:Size(max = 16, message = "验证码格式错误")
        val emailCode: String,
    )

    data class ResetPasswordRequest(
        @field:NotBlank(message = "邮箱不能为空")
        @field:Email(message = "邮箱格式不正确")
        @field:Size(max = 128, message = "邮箱不能超过 128 个字符")
        val email: String,
        @field:NotBlank(message = "验证码不能为空")
        @field:Size(max = 16, message = "验证码格式错误")
        val emailCode: String,
        @field:Size(min = 8, max = 72, message = "新密码长度必须为 8 到 72 个字符")
        val newPassword: String,
    )

    fun login(req: LoginRequest, clientIp: String = ""): LoginResponse {
        val accountIdentity = req.username.trim().lowercase()
        ensureLoginAllowed(accountIdentity, clientIp)
        val user = userService.findByUsername(req.username)
            ?: rejectLogin(accountIdentity, clientIp, UsernameOrPasswordErrorException())
        if (user.status == Status.BANNED) {
            rejectLogin(accountIdentity, clientIp, UserDisabledException())
        }
        if (!matchesPassword(req.password, user.password)) {
            rejectLogin(accountIdentity, clientIp, UsernameOrPasswordErrorException())
        }
        clearLoginFailures(accountIdentity, clientIp)
        return issueTokens(user)
    }

    /** 邮箱验证登录：校验邮箱验证码（绑定 email+UA）+ 账号归属 + 密码。 */
    fun loginByEmail(req: EmailLoginRequest, userAgent: String, clientIp: String = ""): LoginResponse {
        val emailIdentity = userService.normalizeEmail(req.email)
        ensureLoginAllowed(emailIdentity, clientIp)
        val user = userService.findByEmail(emailIdentity)
            ?: rejectLogin(emailIdentity, clientIp, UsernameOrPasswordErrorException())
        if (user.status == Status.BANNED) {
            rejectLogin(emailIdentity, clientIp, UserDisabledException())
        }
        // account 必须与该邮箱归属账号的用户名一致，防止用他人邮箱验证码登录任意账号
        if (user.username != req.account.trim()) {
            rejectLogin(emailIdentity, clientIp, UsernameOrPasswordErrorException())
        }
        if (!matchesPassword(req.password, user.password)) {
            rejectLogin(emailIdentity, clientIp, UsernameOrPasswordErrorException())
        }
        // 账号与密码通过后再消费验证码，避免输错密码导致一次性验证码无故作废。
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.EMAIL_LOGIN,
                email = user.email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        clearLoginFailures(emailIdentity, clientIp)
        return issueTokens(user)
    }

    fun logout(jti: String, userId: Long, refreshToken: String?) {
        tokenStore.invalidateAccess(jti, userId)
        // 一并服务端吊销刷新令牌：仅清浏览器 cookie 不够，已泄露的 refresh 值仍可换出新令牌
        refreshToken?.let { revokeRefreshSafely(it, userId) }
    }

    /** 解析刷新令牌 jti 并服务端删除；过期 / 无效则静默跳过——登出不应因 cookie 中令牌失效而失败。 */
    private fun revokeRefreshSafely(refreshToken: String, userId: Long) {
        val claims = runCatching { jwtService.parse(refreshToken) }.getOrNull() ?: return
        if (claims.get(CLAIM_TYPE, String::class.java) != TYPE_REFRESH) return
        if (claims.subject?.toLongOrNull() != userId) return
        val jti = claims.id ?: return
        tokenStore.revokeRefresh(jti, userId)
    }

    fun refresh(refreshToken: String): LoginResponse {
        val claims = jwtService.parse(refreshToken) // 签名 + 过期校验，失败抛 Token*Exception
        if (claims.get(CLAIM_TYPE, String::class.java) != TYPE_REFRESH) {
            throw TokenInvalidException("非刷新令牌")
        }
        val jti = claims.id ?: throw TokenInvalidException()
        val userId = claims.subject?.toLongOrNull() ?: throw TokenInvalidException()
        val storedUserId = tokenStore.consumeRefresh(jti)
            ?: throw TokenInvalidException("刷新令牌已失效")
        if (storedUserId != userId) throw TokenInvalidException()
        // 重新读取用户，确保刷新后 token 内角色与当前一致
        val user = userService.findById(userId)
            ?: throw TokenInvalidException("用户不存在")
        // 被禁用的账号不得凭旧刷新令牌续期，封禁后立即生效
        if (user.status == Status.BANNED) throw UserDisabledException()
        return issueTokens(user)
    }

    /** 发送验证码：校验场景前置条件 + 限流，生成后发邮件。 */
    fun sendVerificationCode(
        req: SendCodeRequest,
        userAgent: String,
        authenticatedUserId: Long? = null,
        clientIp: String = "",
    ) {
        val email = userService.normalizeEmail(req.email)
        rateLimiter.consume("verification-code-ip", clientIp, MAX_CODE_REQUESTS_PER_IP, CODE_REQUEST_WINDOW)
        val contextUserId: Long?
        val shouldSend: Boolean
        when (req.scene) {
            VerificationCodeService.Scene.REGISTER -> {
                contextUserId = null
                shouldSend = userService.findByEmail(email) == null
            }

            VerificationCodeService.Scene.RESET_PASSWORD,
            VerificationCodeService.Scene.EMAIL_LOGIN -> {
                contextUserId = null
                shouldSend = userService.findByEmail(email) != null
            }

            VerificationCodeService.Scene.CHANGE_PASSWORD -> {
                val userId = authenticatedUserId
                    ?: throw UnauthorizedException("修改密码验证码需要先登录")
                val user = userService.findById(userId)
                    ?: throw UnauthorizedException("当前登录用户不存在")
                if (user.status == Status.BANNED) throw UserDisabledException()
                if (!user.email.equals(email, ignoreCase = true)) {
                    throw ParamErrorException("邮箱与当前账号不匹配")
                }
                contextUserId = userId
                shouldSend = true
            }
        }
        verificationCodeService.checkAndRecordSend(email)
        // 匿名场景统一返回成功，避免通过发码接口探测邮箱是否已注册。
        if (!shouldSend) return
        val ctx = VerificationCodeService.CodeContext(
            scene = req.scene,
            email = email,
            userId = contextUserId,
            userAgent = userAgent,
        )
        val code = verificationCodeService.generate(ctx)
        mailService.sendVerificationCode(email, code, req.scene)
    }

    /** 项目管理凭一次性邀请码注册：先校验邮箱验证码，再建号 + 原子消费邀请码；消费失败则回滚。 */
    @Transactional
    fun registerManager(
        req: RegisterManagerRequest,
        userAgent: String,
        clientIp: String = "",
    ): RegisterManagerResponse {
        rateLimiter.consume("manager-register-ip", clientIp, MAX_REGISTRATIONS_PER_IP, REGISTRATION_WINDOW)
        val user = userService.createUser(req.username, req.password, req.email, Role.PROJECT_MANAGER)
        if (!inviteCodeService.consume(req.inviteCode, user.id!!)) {
            // 邀请码无效 / 已用 / 已过期：同一事务回滚，不留下无邀请码的用户
            throw ParamErrorException("邀请码无效或已过期")
        }
        // 放在本地字段校验、建号与邀请码消费之后：前置步骤失败时不会白白消耗验证码。
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.REGISTER,
                email = user.email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        return RegisterManagerResponse(
            id = user.id!!,
            username = user.username,
            role = requireNotNull(user.role) { "新建用户缺少角色" },
        )
    }

    /** 修改密码（已登录）：校验旧密码 + 邮箱验证码确认后更新。 */
    @Transactional
    fun changePassword(userId: Long, req: ChangePasswordRequest, userAgent: String) {
        val user = userService.findById(userId) ?: throw UserNotFoundException()
        if (!matchesPassword(req.oldPassword, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        val email = userService.normalizeEmail(req.email)
        if (!user.email.equals(email, ignoreCase = true)) {
            throw ParamErrorException("邮箱与当前账号不匹配")
        }
        userService.validatePassword(req.newPassword)
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.CHANGE_PASSWORD,
                email = email,
                userId = userId,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        userService.updatePassword(user, req.newPassword)
        // 改密码后踢掉所有旧会话，强制重新登录
        tokenStore.invalidateAllSessions(userId)
    }

    /** 找回密码（匿名）：凭邮箱验证码重置密码。 */
    @Transactional
    fun resetPassword(req: ResetPasswordRequest, userAgent: String, clientIp: String = "") {
        val email = userService.normalizeEmail(req.email)
        rateLimiter.consume("password-reset-ip", clientIp, MAX_RESETS_PER_IP, PASSWORD_RESET_WINDOW)
        rateLimiter.consume("password-reset-email", email, MAX_RESETS_PER_EMAIL, PASSWORD_RESET_WINDOW)
        userService.validatePassword(req.newPassword)
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.RESET_PASSWORD,
                email = email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.findByEmail(email) ?: throw VerificationCodeInvalidException()
        userService.updatePassword(user, req.newPassword)
        tokenStore.invalidateAllSessions(user.id!!)
    }

    private fun ensureLoginAllowed(accountIdentity: String, clientIp: String) {
        rateLimiter.ensureNotLocked(LOGIN_ACCOUNT_NAMESPACE, accountIdentity)
        rateLimiter.ensureNotLocked(LOGIN_IP_NAMESPACE, clientIp)
    }

    private fun rejectLogin(accountIdentity: String, clientIp: String, exception: BusinessException): Nothing {
        val accountLocked = rateLimiter.recordFailure(
            LOGIN_ACCOUNT_NAMESPACE,
            accountIdentity,
            MAX_LOGIN_FAILURES_PER_ACCOUNT,
            LOGIN_FAILURE_WINDOW,
            LOGIN_LOCK_DURATION,
        )
        val ipLocked = rateLimiter.recordFailure(
            LOGIN_IP_NAMESPACE,
            clientIp,
            MAX_LOGIN_FAILURES_PER_IP,
            LOGIN_FAILURE_WINDOW,
            LOGIN_LOCK_DURATION,
        )
        if (accountLocked || ipLocked) {
            throw TooManyRequestsException("登录失败次数过多，请稍后重试")
        }
        throw exception
    }

    private fun clearLoginFailures(accountIdentity: String, clientIp: String) {
        rateLimiter.clearFailures(LOGIN_ACCOUNT_NAMESPACE, accountIdentity)
        rateLimiter.clearFailures(LOGIN_IP_NAMESPACE, clientIp)
    }

    private fun issueTokens(user: User): LoginResponse {
        val role = requireNotNull(user.role) { "用户缺少角色" }.name
        val userId = user.id!!
        val access = jwtService.issueAccessToken(userId, user.username, role, props.accessTokenTtlSeconds)
        val refresh = jwtService.issueRefreshToken(userId, user.username, role, props.refreshTokenTtlSeconds)
        tokenStore.saveAccess(access.jti, userId, Duration.ofSeconds(access.ttlSeconds))
        tokenStore.saveRefresh(refresh.jti, userId, Duration.ofSeconds(refresh.ttlSeconds))
        return LoginResponse(access.token, refresh.token, "Bearer", access.ttlSeconds, refresh.ttlSeconds)
    }

    private fun matchesPassword(rawPassword: String, encodedPassword: String): Boolean {
        if (rawPassword.toByteArray(Charsets.UTF_8).size > MAX_BCRYPT_PASSWORD_BYTES) {
            return false
        }
        return passwordEncoder.matches(rawPassword, encodedPassword)
    }

    private companion object {
        const val TYPE_REFRESH = "refresh"
        const val CLAIM_TYPE = "type"
        const val MAX_BCRYPT_PASSWORD_BYTES = 72
        const val LOGIN_ACCOUNT_NAMESPACE = "login-account"
        const val LOGIN_IP_NAMESPACE = "login-ip"
        const val MAX_LOGIN_FAILURES_PER_ACCOUNT = 5
        const val MAX_LOGIN_FAILURES_PER_IP = 30
        const val MAX_CODE_REQUESTS_PER_IP = 30
        const val MAX_REGISTRATIONS_PER_IP = 10
        const val MAX_RESETS_PER_IP = 20
        const val MAX_RESETS_PER_EMAIL = 5
        val LOGIN_FAILURE_WINDOW: Duration = Duration.ofMinutes(15)
        val LOGIN_LOCK_DURATION: Duration = Duration.ofMinutes(15)
        val CODE_REQUEST_WINDOW: Duration = Duration.ofHours(1)
        val REGISTRATION_WINDOW: Duration = Duration.ofHours(1)
        val PASSWORD_RESET_WINDOW: Duration = Duration.ofHours(1)
    }
}
