package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * JWT 鉴权主体。authorities 为空（不使用 Spring Security 角色体系）；
 * 账号等级通过 [role] 携带，由 [fun.utf8.nekoprojectbackend.service.AccessService] 软判定。
 * 用户封禁通过 Redis 白名单驱逐（见 TokenStore.invalidateAllSessions）实现，故不再携带 status。
 *
 * 注：UserDetails.getUsername() 与 data class 属性 username 生成的 getter 同签名，
 * 故用 @get:JvmName 将属性 getter 改名，再显式 override getUsername() 返回该值。
 */
data class LoginUser(
    val id: Long,
    @get:JvmName("loginUsername") val username: String,
    val role: Role,
    val jti: String,
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
    override fun getPassword(): String? = null
    override fun getUsername(): String = username
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true

    companion object {
        fun of(user: User, jti: String) =
            LoginUser(user.id!!, user.username, user.role, jti)
    }
}
