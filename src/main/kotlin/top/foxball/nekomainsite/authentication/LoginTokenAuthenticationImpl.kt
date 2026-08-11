package top.foxball.nekomainsite.authentication

import org.springframework.stereotype.Service
import org.springframework.context.annotation.Profile
import top.foxball.nekomainsite.config.JwtProperties
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.entity.redis.LoginToken
import top.foxball.nekomainsite.repository.LoginTokenRepository
import top.foxball.nekomainsite.shared.requirePresent

/**
 * JWT 登录会话管理：负责令牌签发（委托 [JwtService]）与 Redis 白名单（[LoginToken]）的增删查。
 *
 * 白名单用于主动登出/撤销：即便 JWT 本身未过期，从 Redis 删除对应记录即令其失效。
 * 纯签名/验签逻辑见 [JwtService]（无 Spring、无 Redis 依赖）。
 */
@Service
@Profile("!local")
class LoginTokenAuthenticationImpl(
    private val loginTokenRepository: LoginTokenRepository,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
) : LoginTokenAuthentication {

    override fun isValid(userId: Long, token: String, userAgent: String): Boolean {
        // 仅校验白名单与 UA 绑定；JWT 签名/过期由调用方（JwtAuthenticationFilter）先行验签
        val record = loginTokenRepository.findById(token).orElse(null) ?: return false
        return record.userId == userId && record.userAgent == userAgent
    }

    override fun createToken(userId: Long, userAgent: String): LoginToken {
        val token = jwtService.issue(userId, jwtProperties.ttlSeconds).token
        // 以 JWT 作为白名单主键：删除该记录即等价于撤销/登出此令牌
        val record = LoginToken().apply {
            id = token
            this.userId = userId
            this.userAgent = userAgent
            ttlSeconds = jwtProperties.ttlSeconds
        }
        return loginTokenRepository.save(record)
    }

    override fun deleteToken(token: String) {
        loginTokenRepository.deleteById(token)
    }

    override fun deleteToken(token: LoginToken) {
        loginTokenRepository.delete(token)
    }

    override fun findAll(userId: Long): MutableList<LoginToken?>? =
        loginTokenRepository.findByUserId(userId)

    override fun login(user: User, userAgent: String): LoginTokenAuthentication.LoginResult {
        // 凭据已由 AuthService 校验，此处仅签发令牌并组装登录响应
        val userId = user.id.requirePresent("User.id")
        val token = createToken(userId, userAgent).id.requirePresent("LoginToken.id")
        return LoginTokenAuthentication.LoginResult(
            state = LoginTokenAuthentication.LoginResult.State.SUCCESS,
            response = LoginTokenAuthentication.LoginResult.Response(token = token, userId = userId),
        )
    }
}
