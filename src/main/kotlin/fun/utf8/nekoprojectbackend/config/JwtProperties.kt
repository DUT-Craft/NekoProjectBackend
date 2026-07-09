package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** JWT 配置项（security.jwt.*）：密钥、签发方、access/refresh token 有效期。 */
@ConfigurationProperties(prefix = "security.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "NekoBackend",
    val accessTokenTtlSeconds: Long = 7200L,
    val refreshTokenTtlSeconds: Long = 604800L,
)
