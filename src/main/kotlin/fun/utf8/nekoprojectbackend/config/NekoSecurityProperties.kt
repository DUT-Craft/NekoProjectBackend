package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 安全相关自定义配置（neko.security.*）。
 *
 * - [trustedProxy]：是否部署在可信反向代理之后。仅 true 时 clientIp 才采信
 *   X-Forwarded-For / X-Real-IP；否则用 remoteAddr，防客户端伪造 XFF 绕过 IP 维度限流（设计 §5.2 修正）。
 */
@ConfigurationProperties(prefix = "neko.security")
data class NekoSecurityProperties(
    val trustedProxy: Boolean = false,
)
