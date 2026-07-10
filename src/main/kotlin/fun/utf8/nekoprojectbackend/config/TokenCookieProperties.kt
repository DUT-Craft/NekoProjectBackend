package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 刷新令牌 Cookie 配置（neko.security.cookie.*）。 */
@ConfigurationProperties(prefix = "neko.security.cookie")
data class TokenCookieProperties(
    /** Cookie 名称。 */
    val name: String = "nekobox_refresh",
    /** 绑定域名；留空则不写 Domain（仅当前 host 生效）。 */
    val domain: String = "",
    /** 路径。 */
    val path: String = "/",
    /** 仅 HTTPS 传输；本地 http 调试可置 false，生产必须 true。 */
    val secure: Boolean = false,
    /** 屏蔽 JS 读取，防 XSS 窃取刷新令牌。 */
    val httpOnly: Boolean = true,
    /** SameSite 策略：Lax / Strict / None（None 时 secure 必须为 true）。 */
    val sameSite: String = "Lax",
)
