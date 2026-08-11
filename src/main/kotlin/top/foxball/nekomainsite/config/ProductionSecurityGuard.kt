package top.foxball.nekomainsite.config

import jakarta.annotation.PostConstruct
import java.net.URI
import java.nio.file.Path
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod")
class ProductionSecurityGuard(
    private val jwtProperties: JwtProperties,
    private val corsProperties: CorsProperties,
    private val fileProperties: FileProperties,
    private val blessingSkinProperties: BlessingSkinProperties,
) {
    @PostConstruct
    fun validate() {
        require(jwtProperties.secret.length >= 32 && jwtProperties.secret != DEVELOPMENT_SECRET) {
            "JWT_SECRET must be replaced with a random production secret of at least 32 characters"
        }
        require(jwtProperties.cookieSecure) { "AUTH_COOKIE_SECURE must remain enabled in production" }
        require(corsProperties.allowedOriginPatterns.isNotEmpty() && corsProperties.allowedOriginPatterns.none(::isUnsafeOrigin)) {
            "CORS_ALLOWED_ORIGINS must contain only explicit HTTPS frontend origins"
        }
        require(isExplicitHttpsOrigin(fileProperties.baseUrl)) {
            "PUBLIC_BASE_URL must be one explicit deployed HTTPS origin without a path, query, fragment, wildcard, or credentials"
        }
        require(runCatching { Path.of(fileProperties.storagePath).isAbsolute }.getOrDefault(false)) {
            "FILE_STORAGE_PATH must be an absolute persistent directory in production"
        }
        require(!blessingSkinProperties.enabled || blessingSkinProperties.hasValidConfiguration(httpsOnly = true)) {
            "Enabled Blessing Skin OAuth must provide complete HTTPS endpoints, credentials, redirect URI, and valid timeouts"
        }
    }

    private fun isUnsafeOrigin(value: String): Boolean = !isExplicitHttpsOrigin(value)

    private fun isExplicitHttpsOrigin(value: String): Boolean {
        val origin = value.trim()
        if (origin.isEmpty() || "*" in origin) return false
        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.path.isNullOrEmpty() &&
            uri.query == null &&
            uri.fragment == null &&
            uri.port in -1..65535 &&
            host != "localhost" &&
            !host.startsWith("127.") &&
            host != "0.0.0.0" &&
            host != "::1" &&
            host != "[::1]"
    }

    private companion object {
        const val DEVELOPMENT_SECRET = "dev-secret-do-not-use-in-prod-please-override-via-JWT_SECRET"
    }
}
