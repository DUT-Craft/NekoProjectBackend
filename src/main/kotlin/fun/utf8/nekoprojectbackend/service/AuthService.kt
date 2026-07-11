package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.handlder.UserDisabledException
import `fun`.utf8.nekoprojectbackend.handlder.UsernameOrPasswordErrorException
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 鉴权业务：登录（密码校验 + 签发 token 并写白名单）、登出（白名单驱逐）、
 * 刷新令牌（一次性消费，刷新时重读用户以同步角色）、项目管理凭邀请码注册。
 */
@Service
class AuthService(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val tokenStore: TokenStore,
    private val inviteCodeService: InviteCodeService,
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
    )

    data class RegisterManagerResponse(
        val id: Long,
        val username: String,
        val role: Role,
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

    /** 项目管理凭一次性邀请码注册：先建号，再原子消费邀请码（一码一次）；消费失败则回滚刚创建的用户。 */
    @Transactional
    fun registerManager(req: RegisterManagerRequest): RegisterManagerResponse {
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
