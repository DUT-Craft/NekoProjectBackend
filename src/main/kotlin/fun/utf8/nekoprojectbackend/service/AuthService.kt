package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.*
import jakarta.transaction.Transactional
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
    private val props: JwtProperties,
) {

    data class LoginRequest(val username: String, val password: String)
    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshExpiresIn: Long,
    )

    data class RegisterManagerRequest(
        val inviteCode: String,
        val username: String,
        val password: String,
        val email: String,
        val emailCode: String,
    )

    data class RegisterManagerResponse(
        val id: Long,
        val username: String,
        val role: Role,
    )

    /** 邮箱+密码登录请求：username 可为用户名或邮箱，需额外校验邮箱验证码。 */
    data class EmailLoginRequest(
        val account: String,
        val password: String,
        val email: String,
        val emailCode: String,
    )

    data class SendCodeRequest(
        val email: String,
        val scene: VerificationCodeService.Scene,
        val userId: Long? = null,
    )

    data class ChangePasswordRequest(
        val oldPassword: String,
        val newPassword: String,
        val email: String,
        val emailCode: String,
    )

    data class ResetPasswordRequest(
        val email: String,
        val emailCode: String,
        val newPassword: String,
    )

    fun login(req: LoginRequest): LoginResponse {
        val user = userService.findByUsername(req.username)
            ?: throw UsernameOrPasswordErrorException()
        if (user.status == Status.BANNED) throw UserDisabledException()
        if (!passwordEncoder.matches(req.password, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        return issueTokens(user)
    }

    /** 邮箱验证登录：校验邮箱验证码（绑定 email+UA）+ 密码。 */
    fun loginByEmail(req: EmailLoginRequest, userAgent: String): LoginResponse {
        // 校验验证码（匿名场景，按 email+UA 绑定）
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.EMAIL_LOGIN,
                email = req.email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.findByEmail(req.email)
            ?: throw UsernameOrPasswordErrorException()
        if (user.status == Status.BANNED) throw UserDisabledException()
        if (user.username != req.account.trim() && user.email != req.email.trim()) {
            throw UsernameOrPasswordErrorException()
        }
        if (!passwordEncoder.matches(req.password, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        return issueTokens(user)
    }

    fun logout(jti: String, userId: Long) {
        tokenStore.invalidateAccess(jti, userId)
    }

    fun refresh(refreshToken: String): LoginResponse {
        val claims = jwtService.parse(refreshToken) // 签名 + 过期校验，失败抛 Token*Exception
        if (claims.get(CLAIM_TYPE, String::class.java) != TYPE_REFRESH) {
            throw TokenInvalidException("非刷新令牌")
        }
        val jti = claims.id ?: throw TokenInvalidException()
        val userId = claims.subject.toLong()
        val storedUserId = tokenStore.consumeRefresh(jti)
            ?: throw TokenInvalidException("刷新令牌已失效")
        if (storedUserId != userId) throw TokenInvalidException()
        // 重新读取用户，确保刷新后 token 内角色与当前一致
        val user = userService.findById(userId)
            ?: throw TokenInvalidException("用户不存在")
        return issueTokens(user)
    }

    /** 发送验证码：校验场景前置条件 + 限流，生成后发邮件。 */
    fun sendVerificationCode(req: SendCodeRequest, userAgent: String) {
        val email = req.email.trim()
        if (email.isBlank()) throw ParamErrorException("邮箱不能为空")
        // 场景前置校验：找回密码要求邮箱已注册；注册要求邮箱未注册
        when (req.scene) {
            VerificationCodeService.Scene.REGISTER -> {
                if (userService.findByEmail(email) != null) {
                    throw ParamErrorException("该邮箱已注册")
                }
            }

            VerificationCodeService.Scene.RESET_PASSWORD,
            VerificationCodeService.Scene.EMAIL_LOGIN -> {
                if (userService.findByEmail(email) == null) {
                    throw UserNotFoundException("该邮箱未注册")
                }
            }

            VerificationCodeService.Scene.CHANGE_PASSWORD -> {
                // 改密码确认：必须带 userId 且与邮箱归属一致
                val userId = req.userId ?: throw ParamErrorException("缺少用户身份")
                val user = userService.findById(userId) ?: throw UserNotFoundException()
                if (user.email != email) throw ParamErrorException("邮箱与当前用户不匹配")
            }
        }
        verificationCodeService.checkAndRecordSend(email)
        val ctx = VerificationCodeService.CodeContext(
            scene = req.scene,
            email = email,
            userId = if (req.scene == VerificationCodeService.Scene.CHANGE_PASSWORD) req.userId else null,
            userAgent = userAgent,
        )
        val code = verificationCodeService.generate(ctx)
        mailService.sendVerificationCode(email, code, req.scene)
    }

    /** 项目管理凭一次性邀请码注册：先校验邮箱验证码，再建号 + 原子消费邀请码；消费失败则回滚。 */
    @Transactional
    fun registerManager(req: RegisterManagerRequest, userAgent: String): RegisterManagerResponse {
        // 校验注册验证码（匿名场景，按 email+UA 绑定）
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.REGISTER,
                email = req.email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.createUser(req.username, req.password, req.email, Role.PROJECT_MANAGER)
        if (!inviteCodeService.consume(req.inviteCode, user.id!!)) {
            // 邀请码无效 / 已用 / 已过期：同一事务回滚，不留下无邀请码的用户
            throw ParamErrorException("邀请码无效或已过期")
        }
        return RegisterManagerResponse(
            id = user.id!!,
            username = user.username,
            role = user.role ?: Role.PROJECT_MANAGER,
        )
    }

    /** 修改密码（已登录）：校验旧密码 + 邮箱验证码确认后更新。 */
    @Transactional
    fun changePassword(userId: Long, req: ChangePasswordRequest, userAgent: String) {
        val user = userService.findById(userId) ?: throw UserNotFoundException()
        if (!passwordEncoder.matches(req.oldPassword, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.CHANGE_PASSWORD,
                email = req.email,
                userId = userId,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        if (req.newPassword.isBlank()) throw ParamErrorException("新密码不能为空")
        user.password = passwordEncoder.encode(req.newPassword)
            ?: throw IllegalStateException("Password encoding failed.")
        userService.save(user)
        // 改密码后踢掉所有旧会话，强制重新登录
        tokenStore.invalidateAllSessions(userId)
    }

    /** 找回密码（匿名）：凭邮箱验证码重置密码。 */
    @Transactional
    fun resetPassword(req: ResetPasswordRequest, userAgent: String) {
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.RESET_PASSWORD,
                email = req.email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.findByEmail(req.email) ?: throw UserNotFoundException()
        if (req.newPassword.isBlank()) throw ParamErrorException("新密码不能为空")
        user.password = passwordEncoder.encode(req.newPassword)
            ?: throw IllegalStateException("Password encoding failed.")
        userService.save(user)
        tokenStore.invalidateAllSessions(user.id!!)
    }

    private fun issueTokens(user: User): LoginResponse {
        val role = (user.role ?: Role.PROJECT_MANAGER).name
        val userId = user.id!!
        val access = jwtService.issueAccessToken(userId, user.username, role, props.accessTokenTtlSeconds)
        val refresh = jwtService.issueRefreshToken(userId, user.username, role, props.refreshTokenTtlSeconds)
        tokenStore.saveAccess(access.jti, userId, Duration.ofSeconds(access.ttlSeconds))
        tokenStore.saveRefresh(refresh.jti, userId, Duration.ofSeconds(refresh.ttlSeconds))
        return LoginResponse(access.token, refresh.token, "Bearer", access.ttlSeconds, refresh.ttlSeconds)
    }

    private companion object {
        const val TYPE_REFRESH = "refresh"
        const val CLAIM_TYPE = "type"
    }
}
