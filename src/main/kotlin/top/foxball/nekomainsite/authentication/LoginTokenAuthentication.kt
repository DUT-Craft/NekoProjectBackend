package top.foxball.nekomainsite.authentication

import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.entity.redis.LoginToken

/**
 * 登录会话与令牌生命周期服务：签发/校验/撤销令牌，并组装登录响应。
 *
 * - 令牌采用 JWT（HS256），签名/验签由 [JwtService] 完成；
 * - 会话白名单（撤销查询）落 Redis，见 [LoginToken]。
 */
interface LoginTokenAuthentication {
    fun isValid(userId: Long, token: String, userAgent: String): Boolean
    fun createToken(userId: Long, userAgent: String): LoginToken
    fun deleteToken(token: String)
    fun deleteToken(token: LoginToken)
    fun findAll(userId: Long): MutableList<LoginToken?>?

    fun login(user: User, userAgent: String): LoginResult

    data class LoginResult(
        val state: State,
        val response: Response? = null
    ) {
        enum class State {
            SUCCESS,
            GROUP_NOT_ALLOWED,
        }

        data class Response(
            val token: String,
            val userId: Long,
        )
    }
}
