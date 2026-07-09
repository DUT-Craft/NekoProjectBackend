package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.handlder.UserDisabledException
import `fun`.utf8.nekoprojectbackend.handlder.UsernameOrPasswordErrorException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

/** 鉴权业务：登录（密码校验 + 签发 token 并写白名单）、登出（白名单驱逐）、刷新令牌（一次性消费）。 */
@Service
class AuthService(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val tokenStore: TokenStore,
    private val props: JwtProperties,
) {

    data class LoginRequest(val username: String, val password: String)
    data class RefreshRequest(val refreshToken: String)
    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresIn: Long,
    )

    fun login(req: LoginRequest): LoginResponse {
        val user = userService.findByUsername(req.username)
            ?: throw UsernameOrPasswordErrorException()
        if (user.status == Status.BANNED) throw UserDisabledException()
        if (!passwordEncoder.matches(req.password, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        return issueTokens(user.id!!, user.username)
    }

    fun logout(jti: String, userId: Long) {
        tokenStore.invalidateAccess(jti, userId)
    }

    fun refresh(req: RefreshRequest): LoginResponse {
        val claims = jwtService.parse(req.refreshToken) // 签名 + 过期校验，失败抛 Token*Exception
        if (claims.get(CLAIM_TYPE, String::class.java) != TYPE_REFRESH) {
            throw TokenInvalidException("非刷新令牌")
        }
        val jti = claims.id ?: throw TokenInvalidException()
        val userId = claims.subject.toLong()
        val storedUserId = tokenStore.consumeRefresh(jti)
            ?: throw TokenInvalidException("刷新令牌已失效")
        if (storedUserId != userId) throw TokenInvalidException()
        val username = claims.get(CLAIM_USERNAME, String::class.java)
        return issueTokens(userId, username)
    }

    private fun issueTokens(userId: Long, username: String): LoginResponse {
        val access = jwtService.issueAccessToken(userId, username, props.accessTokenTtlSeconds)
        val refresh = jwtService.issueRefreshToken(userId, username, props.refreshTokenTtlSeconds)
        tokenStore.saveAccess(access.jti, userId, Duration.ofSeconds(access.ttlSeconds))
        tokenStore.saveRefresh(refresh.jti, userId, Duration.ofSeconds(refresh.ttlSeconds))
        return LoginResponse(access.token, refresh.token, "Bearer", access.ttlSeconds)
    }

    private companion object {
        const val TYPE_REFRESH = "refresh"
        const val CLAIM_TYPE = "type"
        const val CLAIM_USERNAME = "username"
    }
}
