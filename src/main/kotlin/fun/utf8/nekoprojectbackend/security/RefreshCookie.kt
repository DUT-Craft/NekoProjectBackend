package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.config.TokenCookieProperties
import jakarta.servlet.http.Cookie
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/** 刷新令牌 Cookie 的生成 / 清除 / 读取工具，集中维护 HttpOnly/Secure/SameSite 等属性。 */
@Component
class RefreshCookie(private val props: TokenCookieProperties) {

    /** 生成下发刷新令牌的 Set-Cookie 值。 */
    fun setCookie(token: String, maxAgeSeconds: Long): String = base()
        .value(token)
        .maxAge(maxAgeSeconds)
        .build()
        .toString()

    /** 生成清除 Cookie 的 Set-Cookie 值（Max-Age=0）。 */
    fun clearCookie(): String = base()
        .value("")
        .maxAge(0)
        .build()
        .toString()

    /** 从请求 Cookie 中读取刷新令牌；缺失或空返回 null。 */
    fun read(cookies: Array<Cookie>?): String? =
        cookies?.firstOrNull { it.name == props.name }?.value?.takeIf { it.isNotBlank() }

    private fun base(): ResponseCookie.ResponseCookieBuilder {
        val builder = ResponseCookie.from(props.name)
            .httpOnly(props.httpOnly)
            .secure(props.secure)
            .path(props.path)
            .sameSite(props.sameSite)
        props.domain.takeIf { it.isNotBlank() }?.let { builder.domain(it) }
        return builder
    }
}
