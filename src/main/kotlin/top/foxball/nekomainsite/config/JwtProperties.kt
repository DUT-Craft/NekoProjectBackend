package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 配置（neko.security.jwt.*）。
 *
 * - [secret] HS256 签名密钥；生产环境务必通过环境变量 `JWT_SECRET` 覆盖默认占位值。
 * - [ttlSeconds] 访问令牌有效期（秒），默认与 [top.foxball.nekomainsite.entity.redis.LoginToken]
 *   的 `@RedisHash timeToLive`（3 天 = 259200s）对齐 —— 令牌到期时白名单记录也几乎同时被 Redis 淘汰。
 */
@ConfigurationProperties(prefix = "neko.security.jwt")
data class JwtProperties(
    val secret: String = "dev-secret-do-not-use-in-prod-please-override-via-JWT_SECRET",
    val ttlSeconds: Long = 259200L,
    val cookieSecure: Boolean = false,
)
