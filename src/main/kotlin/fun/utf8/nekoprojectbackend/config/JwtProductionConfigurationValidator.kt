package `fun`.utf8.nekoprojectbackend.config

import org.springframework.context.annotation.Profile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.net.URI

/** 生产 profile 的 JWT 配置硬校验，避免使用开发密钥或无效的令牌有效期启动服务。 */
@Component
@Profile("prod")
class JwtProductionConfigurationValidator(
    props: JwtProperties,
    cookieProps: TokenCookieProperties,
    fileProps: FileProperties,
    @Value("\${neko.cors.allowed-origins}") allowedOrigins: String,
) {
    init {
        validate(props)
        validateCookie(cookieProps)
        validateOrigins(allowedOrigins)
        validateFileBaseUrl(fileProps.baseUrl)
    }

    private fun validateCookie(props: TokenCookieProperties) {
        if (!props.secure || !props.httpOnly) {
            throw IllegalStateException("生产环境 refresh Cookie 必须启用 Secure 和 HttpOnly")
        }
        val sameSite = props.sameSite.trim().lowercase()
        if (sameSite !in setOf("lax", "strict", "none")) {
            throw IllegalStateException("COOKIE_SAME_SITE 只能是 Lax、Strict 或 None")
        }
        if (sameSite == "none" && !props.secure) {
            throw IllegalStateException("SameSite=None 时 refresh Cookie 必须启用 Secure")
        }
    }

    private fun validateOrigins(value: String) {
        val origins = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (origins.isEmpty()) {
            throw IllegalStateException("生产环境必须设置 CORS_ALLOWED_ORIGINS")
        }
        origins.forEach { origin ->
            val uri = runCatching { URI(origin) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank() ||
                (!uri.path.isNullOrEmpty() && uri.path != "/") || uri.query != null || uri.fragment != null
            ) {
                throw IllegalStateException("生产环境 CORS 来源必须是 HTTPS origin：$origin")
            }
        }
    }

    private fun validateFileBaseUrl(value: String) {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        val host = uri?.host?.lowercase()
        if (uri?.scheme != "https" || host.isNullOrBlank() ||
            host in LOCAL_FILE_HOSTS || uri.userInfo != null ||
            (!uri.path.isNullOrEmpty() && uri.path != "/") || uri.query != null || uri.fragment != null
        ) {
            throw IllegalStateException("生产环境 FILE_BASE_URL 必须是外部可访问的 HTTPS origin")
        }
    }

    private fun validate(props: JwtProperties) {
        val secret = props.secret.trim()
        if (secret.isBlank()) {
            throw IllegalStateException("生产环境必须设置 JWT_SECRET")
        }
        if (secret.toByteArray(StandardCharsets.UTF_8).size < MIN_SECRET_BYTES) {
            throw IllegalStateException("生产环境 JWT_SECRET 至少需要 $MIN_SECRET_BYTES 字节")
        }
        if (INSECURE_SECRETS.any { it.equals(secret, ignoreCase = true) }) {
            throw IllegalStateException("生产环境不能使用示例或开发用 JWT_SECRET")
        }
        if (props.issuer.isBlank()) {
            throw IllegalStateException("生产环境 security.jwt.issuer 不能为空")
        }
        if (props.accessTokenTtlSeconds <= 0L || props.refreshTokenTtlSeconds <= 0L) {
            throw IllegalStateException("生产环境 JWT 令牌有效期必须大于 0")
        }
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
        val INSECURE_SECRETS = setOf(
            "neko-backend-local-dev-secret-2026-change-me",
            "replace-with-at-least-32-bytes-random-string",
            "change-me",
            "password",
        )
        val LOCAL_FILE_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
    }
}
