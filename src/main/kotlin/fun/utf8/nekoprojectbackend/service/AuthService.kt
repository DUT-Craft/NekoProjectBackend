package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.*
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * 鉴权业务：统一 account 登录（用户名或邮箱）、邮箱验证登录、登出（白名单驱逐）、
 * 刷新令牌（一次性消费，刷新时重读用户以同步角色）、注册、修改密码（需验证码确认）、
 * 找回密码、找回用户名。
 *
 * 邮箱验证码与「场景 + 用户标识 + UserAgent」绑定（见 [VerificationCodeService]）：
 * 匿名场景（注册 / 找回密码 / 找回用户名）用 email 绑定；已登录场景（改密码确认）用 userId 绑定。
 *
 * 密码强度统一走 [PasswordPolicy]（设计 §6.1）；改密码只认已认证用户绑定邮箱（设计 §6.2 / §14.9）。
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

    /** 统一登录请求：account 可为用户名或邮箱（设计 §5.1）。 */
    data class LoginRequest(val account: String, val password: String)
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
        val confirmPassword: String,
        val email: String,
        val emailCode: String,
    )

    data class RegisterManagerResponse(
        val id: Long,
        val username: String,
        val role: Role,
    )

    /** 普通用户公开注册请求（设计 §4.1，无需邀请码）。 */
    data class RegisterUserRequest(
        val username: String,
        val password: String,
        val confirmPassword: String,
        val email: String,
        val emailCode: String,
    )

    data class RegisterUserResponse(
        val id: Long,
        val username: String,
        val role: Role,
    )

    /** 已注册用户凭邀请码补授项目创建资格（设计 §4.2）。 */
    data class CreateProjectGrantRequest(
        val inviteCode: String,
    )

    /** 邮箱+密码登录请求：account 可为用户名或邮箱，需额外校验邮箱验证码。 */
    data class EmailLoginRequest(
        val account: String,
        val password: String,
        val email: String,
        val emailCode: String,
    )

    /** 恢复停用账号请求（匿名，设计 §10 修正）：邮箱 + 密码 + 邮箱验证码。 */
    data class ReactivateRequest(
        val email: String,
        val password: String,
        val emailCode: String,
    )

    data class SendCodeRequest(
        val email: String = "",
        val scene: VerificationCodeService.Scene,
        val userId: Long? = null,
    )

    /** 修改密码请求（已登录）：不携带 email——服务端使用已认证用户绑定邮箱（设计 §6.2 / §14.9）。 */
    data class ChangePasswordRequest(
        val oldPassword: String,
        val newPassword: String,
        val confirmPassword: String,
        val emailCode: String,
    )

    data class ResetPasswordRequest(
        val email: String,
        val emailCode: String,
        val newPassword: String,
        val confirmPassword: String,
    )

    data class RecoverUsernameRequest(
        val email: String,
        val emailCode: String,
    )

    /** 统一账号登录：account 支持用户名（大小写不敏感）或邮箱。先校验密码，再查状态（不暴露账号存在性）。 */
    fun login(req: LoginRequest, userAgent: String, ip: String): LoginResponse {
        val key = req.account.trim()
        val accountKey = key.lowercase()
        // 限流：账号维度 + IP 维度双检查（设计 §5.2）
        rateLimiter.ensureNotLocked(LOGIN_USER_NS, accountKey)
        rateLimiter.ensureNotLocked(LOGIN_IP_NS, ip)
        val user = locateAccount(key)
        if (user == null || !passwordEncoder.matches(req.password, user.password)) {
            // 账号维度失败计数仅对真实存在的账号累加（设计 §5.2 修正）：
            // 避免攻击者对任意输入 account 反复失败，把目标账号锁定造成登录 DoS。
            // 不存在账号仍受 IP 维度限流约束。统一报「账号或密码错误」不暴露原因。
            if (user != null) {
                rateLimiter.recordFailAndCheckLock(LOGIN_USER_NS, accountKey, LOGIN_MAX_FAIL, LOGIN_WINDOW, LOGIN_LOCK)
            }
            rateLimiter.recordFailAndCheckLock(LOGIN_IP_NS, ip, LOGIN_MAX_FAIL * 2, LOGIN_WINDOW, LOGIN_LOCK)
            throw UsernameOrPasswordErrorException()
        }
        ensureLoginable(user)
        rateLimiter.clearFails(LOGIN_USER_NS, accountKey)
        rateLimiter.clearFails(LOGIN_IP_NS, ip)
        user.lastLoginAt = LocalDateTime.now()
        userService.save(user)
        return issueTokens(user, userAgent, ip)
    }

    /** 邮箱验证登录：校验邮箱验证码（绑定 email+UA）+ 密码 + 状态。 */
    fun loginByEmail(req: EmailLoginRequest, userAgent: String, ip: String): LoginResponse {
        rateLimiter.ensureNotLocked(LOGIN_IP_NS, ip)
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
        if (user == null || !passwordEncoder.matches(req.password, user.password)) {
            rateLimiter.recordFailAndCheckLock(LOGIN_IP_NS, ip, LOGIN_MAX_FAIL * 2, LOGIN_WINDOW, LOGIN_LOCK)
            throw UsernameOrPasswordErrorException()
        }
        ensureLoginable(user)
        rateLimiter.clearFails(LOGIN_IP_NS, ip)
        user.lastLoginAt = LocalDateTime.now()
        userService.save(user)
        return issueTokens(user, userAgent, ip)
    }

    /**
     * 恢复停用账号（匿名，设计 §10 修正）：停用后原会话全部失效、无有效 token，
     * 必须走匿名恢复链路 /api/auth/reactivate。邮箱验证码 + 密码双重确认后
     * 置 ACTIVE 并直接签发新会话。仅 DEACTIVATED 可恢复；其余状态统一报「账号或密码错误」不暴露状态。
     */
    @Transactional
    fun reactivateByEmail(req: ReactivateRequest, userAgent: String, ip: String): LoginResponse {
        rateLimiter.ensureNotLocked(LOGIN_IP_NS, ip)
        val email = req.email.trim().lowercase()
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.REACTIVATE,
                email = email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.findByEmail(email)
        if (user == null || !passwordEncoder.matches(req.password, user.password)) {
            rateLimiter.recordFailAndCheckLock(LOGIN_IP_NS, ip, LOGIN_MAX_FAIL * 2, LOGIN_WINDOW, LOGIN_LOCK)
            throw UsernameOrPasswordErrorException()
        }
        if (user.status != Status.DEACTIVATED) {
            // 非 DEACTIVATED（如已封禁 / 已注销）不得通过此链路恢复——不暴露具体状态
            throw UsernameOrPasswordErrorException()
        }
        user.status = Status.ACTIVE
        user.deactivatedAt = null
        user.lastLoginAt = LocalDateTime.now()
        userService.save(user)
        rateLimiter.clearFails(LOGIN_IP_NS, ip)
        return issueTokens(user, userAgent, ip)
    }

    fun logout(jti: String, userId: Long, refreshToken: String?) {
        tokenStore.invalidateAccess(jti, userId)
        refreshToken?.let { revokeRefreshSafely(it, userId) }
    }

    /** 列出当前用户全部有效会话（设备 / UA / IP / 签发时间）。 */
    fun listSessions(userId: Long) = tokenStore.listSessions(userId)

    /** 登出指定设备（按 jti）。 */
    fun invalidateSession(jti: String, userId: Long) = tokenStore.invalidateAccess(jti, userId)

    /** 退出全部设备。 */
    fun invalidateAllSessions(userId: Long) = tokenStore.invalidateAllSessions(userId)

    /** 解析刷新令牌 jti 并服务端删除；过期 / 无效则静默跳过——登出不应因 cookie 中令牌失效而失败。 */
    private fun revokeRefreshSafely(refreshToken: String, userId: Long) {
        val jti = runCatching { jwtService.parse(refreshToken).id }.getOrNull() ?: return
        tokenStore.revokeRefresh(jti, userId)
    }

    fun refresh(refreshToken: String, userAgent: String, ip: String): LoginResponse {
        val claims = jwtService.parse(refreshToken)
        if (claims.get(CLAIM_TYPE, String::class.java) != TYPE_REFRESH) {
            throw TokenInvalidException("非刷新令牌")
        }
        val jti = claims.id ?: throw TokenInvalidException()
        val userId = claims.subject.toLong()
        val storedUserId = tokenStore.consumeRefresh(jti)
            ?: throw TokenInvalidException("刷新令牌已失效")
        if (storedUserId != userId) throw TokenInvalidException()
        val user = userService.findById(userId) ?: throw TokenInvalidException("用户不存在")
        // 刷新时重读状态：被封禁/停用/注销的账号不得凭旧刷新令牌续期
        ensureLoginable(user)
        return issueTokens(user, userAgent, ip)
    }

    /** 取当前登录用户完整信息（/auth/me 用）：重读 DB，返回最新 email / role / canCreateProject。 */
    fun currentUser(userId: Long): User =
        userService.findById(userId) ?: throw UserNotFoundException()

    /** 发送验证码：按场景校验前置条件 + 限流，生成后发邮件。CHANGE_PASSWORD 需登录态（绑定 userId）。 */
    fun sendVerificationCode(req: SendCodeRequest, userAgent: String, loginUser: LoginUser? = null) {
        val scene = req.scene
        when (scene) {
            VerificationCodeService.Scene.REGISTER -> {
                val email = req.email.trim().lowercase()
                requireEmailFormat(email)
                if (userService.findByEmail(email) != null) {
                    throw ParamErrorException("该邮箱已注册")
                }
                sendAnonymousCode(scene, email, userAgent)
            }

            VerificationCodeService.Scene.RESET_PASSWORD,
            VerificationCodeService.Scene.EMAIL_LOGIN -> {
                val email = req.email.trim().lowercase()
                requireEmailFormat(email)
                // 防邮箱枚举（设计 §6.3 修正）：未注册也统一返回成功，不实际发送，不暴露存在性。
                val user = userService.findByEmail(email)
                if (user == null) {
                    sendAnonymousCodeNoOp(email)
                } else {
                    sendAnonymousCode(scene, email, userAgent)
                }
            }

            VerificationCodeService.Scene.RECOVER_USERNAME -> {
                val email = req.email.trim().lowercase()
                requireEmailFormat(email)
                // 不暴露邮箱是否注册：未注册也走「发码」流程（实际不发送），统一返回成功
                val user = userService.findByEmail(email)
                if (user == null) {
                    sendAnonymousCodeNoOp(email)
                } else {
                    sendAnonymousCode(scene, email, userAgent)
                }
            }

            VerificationCodeService.Scene.REACTIVATE -> {
                // 恢复停用账号：账号必须存在且处于 DEACTIVATED 才真正发码。
                // 不暴露存在性 / 状态：不存在或非停用也统一返回成功（不实际发送）。
                val email = req.email.trim().lowercase()
                requireEmailFormat(email)
                val user = userService.findByEmail(email)
                if (user != null && user.status == Status.DEACTIVATED) {
                    sendAnonymousCode(scene, email, userAgent)
                } else {
                    sendAnonymousCodeNoOp(email)
                }
            }

            VerificationCodeService.Scene.CHANGE_PASSWORD -> {
                // 改密码确认：必须登录态下发，绑定 userId（设计 §6.2 / §14.9）。
                // 忽略 req.email——以当前认证用户的绑定邮箱为准，防止用他人邮箱验证码改自己密码。
                val loginUser = loginUser ?: throw UnauthorizedException("修改密码需先登录")
                val fullUser = userService.findById(loginUser.id) ?: throw UserNotFoundException()
                verificationCodeService.checkAndRecordSend(fullUser.email)
                val ctx = VerificationCodeService.CodeContext(
                    scene = scene,
                    email = fullUser.email,
                    userId = loginUser.id,
                    userAgent = userAgent,
                )
                val code = verificationCodeService.generate(ctx)
                mailService.sendVerificationCode(fullUser.email, code, scene)
            }
        }
    }

    private fun sendAnonymousCode(scene: VerificationCodeService.Scene, email: String, userAgent: String) {
        verificationCodeService.checkAndRecordSend(email)
        val ctx = VerificationCodeService.CodeContext(
            scene = scene,
            email = email,
            userId = null,
            userAgent = userAgent,
        )
        val code = verificationCodeService.generate(ctx)
        mailService.sendVerificationCode(email, code, scene)
    }

    /**
     * 占位「发码」：邮箱未注册 / 状态不符时调用，仍走发送限流（占间隔锁 + 计每日上限）但不实际发邮件。
     * 保证未注册与注册邮箱在发码接口上的限流行为一致，降低时序侧信道枚举邮箱的可能（设计 §6.3 修正）。
     * 注：发邮件的网络耗时差异仍可被高精度时序探测，彻底消除需固定响应延迟或异步发信，此处为合理折中。
     */
    private fun sendAnonymousCodeNoOp(email: String) {
        verificationCodeService.checkAndRecordSend(email)
    }

    private fun requireEmailFormat(email: String) {
        if (email.isBlank() || !email.contains('@')) {
            throw ParamErrorException("邮箱格式不正确")
        }
    }

    /** 项目管理凭一次性邀请码注册：先校验邮箱验证码与密码强度，再建号 + 原子消费邀请码。 */
    @Transactional
    fun registerManager(req: RegisterManagerRequest, userAgent: String): RegisterManagerResponse {
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.REGISTER,
                email = req.email.trim().lowercase(),
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        if (req.password != req.confirmPassword) {
            throw ParamErrorException("两次密码不一致")
        }
        PasswordPolicy.validate(req.password, req.username, req.email.substringBefore('@'))
        val user = userService.createUser(req.username, req.password, req.email, Role.USER).also {
            it.emailVerifiedAt = LocalDateTime.now()
            it.canCreateProject = false
        }
        userService.save(user)
        if (!inviteCodeService.consume(req.inviteCode, user.id!!)) {
            throw ParamErrorException("邀请码无效或已过期")
        }
        // 邀请码授予项目创建资格（设计 §4.2 新语义：邀请码 → canCreateProject，而非 PROJECT_MANAGER 角色）
        user.canCreateProject = true
        userService.save(user)
        return RegisterManagerResponse(
            id = user.id!!,
            username = user.username,
            role = user.role,
        )
    }

    /** 普通用户公开注册（设计 §4.1）：校验验证码 + 密码强度 + 用户名策略，建 USER 账号，邮箱视为已验证。 */
    @Transactional
    fun registerUser(req: RegisterUserRequest, userAgent: String): RegisterUserResponse {
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.REGISTER,
                email = req.email.trim().lowercase(),
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        if (req.password != req.confirmPassword) {
            throw ParamErrorException("两次密码不一致")
        }
        PasswordPolicy.validate(req.password, req.username, req.email.substringBefore('@'))
        val user = userService.createUser(req.username, req.password, req.email, Role.USER).also {
            it.emailVerifiedAt = LocalDateTime.now()
            it.canCreateProject = false
        }
        userService.save(user)
        return RegisterUserResponse(
            id = user.id!!,
            username = user.username,
            role = Role.USER,
        )
    }

    /** 已注册用户凭一次性邀请码补授项目创建资格（设计 §4.2 新语义：邀请码 → canCreateProject）。 */
    @Transactional
    fun grantCreateProject(userId: Long, req: CreateProjectGrantRequest) {
        val user = userService.findById(userId) ?: throw UserNotFoundException()
        if (user.role == Role.SUPER_ADMIN) {
            throw ParamErrorException("超级管理员无需补授项目创建资格")
        }
        if (!inviteCodeService.consume(req.inviteCode, userId)) {
            throw ParamErrorException("邀请码无效或已过期")
        }
        user.canCreateProject = true
        userService.save(user)
    }

    /** 修改密码（已登录）：校验旧密码 + 邮箱验证码（绑定本人 userId）+ 密码强度后更新。 */
    @Transactional
    fun changePassword(userId: Long, req: ChangePasswordRequest, userAgent: String) {
        val user = userService.findById(userId) ?: throw UserNotFoundException()
        if (!passwordEncoder.matches(req.oldPassword, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        if (req.newPassword != req.confirmPassword) {
            throw ParamErrorException("两次密码不一致")
        }
        if (req.newPassword == req.oldPassword) {
            throw ParamErrorException("新密码不能与原密码相同")
        }
        PasswordPolicy.validate(req.newPassword, user.username, user.email.substringBefore('@'))
        // 验证码只认本人绑定邮箱 + userId 绑定（设计 §6.2 / §14.9）——忽略客户端任何 email 输入
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.CHANGE_PASSWORD,
                email = user.email,
                userId = userId,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        user.password = passwordEncoder.encode(req.newPassword)!!
        userService.save(user)
        tokenStore.invalidateAllSessions(userId)
        mailService.sendSecurityNotice(user.email, "PASSWORD_CHANGED")
    }

    /** 找回密码（匿名）：凭邮箱验证码重置密码。 */
    @Transactional
    fun resetPassword(req: ResetPasswordRequest, userAgent: String) {
        val email = req.email.trim().lowercase()
        if (req.newPassword != req.confirmPassword) {
            throw ParamErrorException("两次密码不一致")
        }
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.RESET_PASSWORD,
                email = email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        val user = userService.findByEmail(email) ?: throw UserNotFoundException()
        PasswordPolicy.validate(req.newPassword, user.username, user.email.substringBefore('@'))
        user.password = passwordEncoder.encode(req.newPassword)!!
        userService.save(user)
        tokenStore.invalidateAllSessions(user.id!!)
        mailService.sendSecurityNotice(user.email, "PASSWORD_RESET")
    }

    /** 找回用户名（匿名）：凭邮箱验证码把用户名发送到绑定邮箱。不向接口调用方回显用户名（设计 §6.3）。 */
    fun recoverUsername(req: RecoverUsernameRequest, userAgent: String) {
        val email = req.email.trim().lowercase()
        verificationCodeService.verifyAndConsume(
            VerificationCodeService.CodeContext(
                scene = VerificationCodeService.Scene.RECOVER_USERNAME,
                email = email,
                userId = null,
                userAgent = userAgent,
            ),
            req.emailCode,
        )
        // 验证码已校验该邮箱归属；用户不存在则静默（不向调用方暴露存在性）
        val user = userService.findByEmail(email) ?: return
        mailService.sendUsernameReminder(email, user.username)
    }

    /** 按账号键定位用户：含 @ 按邮箱查，否则按用户名归一化列查（大小写不敏感）。 */
    private fun locateAccount(key: String): User? =
        if (key.contains('@')) userService.findByEmail(key.lowercase())
        else userService.findByUsernameLower(key)

    /** 校验账号可登录状态：封禁/停用给出明确提示；注销按「账号或密码错误」处理不暴露存在性（设计 §5.1）。 */
    private fun ensureLoginable(user: User) {
        when (user.status) {
            Status.BANNED -> throw UserDisabledException("账号已被封禁")
            Status.DEACTIVATED -> throw UserDisabledException("账号已停用，请联系管理员恢复")
            Status.DELETED -> throw UsernameOrPasswordErrorException()
            Status.ACTIVE -> { /* 正常 */ }
        }
        if (user.emailVerifiedAt == null) {
            throw UserDisabledException("邮箱未验证，请先完成邮箱验证")
        }
    }

    private fun issueTokens(user: User, userAgent: String, ip: String): LoginResponse {
        val role = user.role.name
        val userId = user.id!!
        val access = jwtService.issueAccessToken(userId, user.username, role, props.accessTokenTtlSeconds)
        val refresh = jwtService.issueRefreshToken(userId, user.username, role, props.refreshTokenTtlSeconds)
        tokenStore.saveAccess(access.jti, userId, userAgent, ip, Duration.ofSeconds(access.ttlSeconds))
        tokenStore.saveRefresh(refresh.jti, userId, Duration.ofSeconds(refresh.ttlSeconds))
        return LoginResponse(access.token, refresh.token, "Bearer", access.ttlSeconds, refresh.ttlSeconds)
    }

    private companion object {
        const val TYPE_REFRESH = "refresh"
        const val CLAIM_TYPE = "type"
        // 登录限流（设计 §5.2）：账号 5 次 / 10min 触发锁定 15min；IP 阈值翻倍
        const val LOGIN_USER_NS = "login:user"
        const val LOGIN_IP_NS = "login:ip"
        const val LOGIN_MAX_FAIL = 5
        const val LOGIN_WINDOW = 600L
        const val LOGIN_LOCK = 900L
    }
}
