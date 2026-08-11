package top.foxball.nekomainsite.service

import top.foxball.nekomainsite.authentication.LoginTokenAuthentication

/**
 * 认证编排：校验凭据并签发会话令牌。
 *
 * 凭据比对 / 用户态校验在此完成；令牌签发与白名单管理委托 [LoginTokenAuthentication]。
 */
interface AuthService {

    /** 按用户名 + 密码登录，返回登录结果（含令牌与用户信息）。 */
    fun login(username: String, password: String, userAgent: String): LoginTokenAuthentication.LoginResult

    /** 返回当前启用用户的公开会话视图；匿名、失效或禁用账号返回 null。 */
    fun currentUser(userId: Long?): AuthUserView?
}

data class AuthUserView(
    val authenticated: Boolean = true,
    val id: Long,
    val username: String,
    val displayName: String,
    val role: String,
    val authSource: String,
)
