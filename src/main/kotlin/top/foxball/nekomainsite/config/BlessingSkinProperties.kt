package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "neko.blessing-skin")
data class BlessingSkinProperties(
    val enabled: Boolean = false,
    val authorizationUrl: String = "",
    val tokenUrl: String = "",
    val userInfoUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "http://127.0.0.1:8080/api/auth/blessing/callback",
    val scope: String = "user",
    val connectTimeoutMs: Int = 3_000,
    val readTimeoutMs: Int = 5_000,
) {
    fun hasValidConfiguration(httpsOnly: Boolean = false): Boolean {
        if (listOf(
                authorizationUrl,
                tokenUrl,
                userInfoUrl,
                clientId,
                clientSecret,
                redirectUri,
            ).any { it.isBlank() }
        ) return false
        if (connectTimeoutMs !in TIMEOUT_RANGE || readTimeoutMs !in TIMEOUT_RANGE) return false
        return listOf(authorizationUrl, tokenUrl, userInfoUrl, redirectUri).all { isHttpUrl(it, httpsOnly) }
    }

    private fun isHttpUrl(value: String, httpsOnly: Boolean): Boolean = runCatching {
        val uri = URI(value.trim())
        val schemes = if (httpsOnly) setOf("https") else setOf("http", "https")
        uri.scheme?.lowercase() in schemes && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private companion object {
        val TIMEOUT_RANGE = 100..60_000
    }
}
