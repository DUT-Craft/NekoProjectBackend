package `fun`.utf8.nekoprojectbackend.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Resolves a stable client address without trusting forwarding headers by default. */
@Component
class ClientRequestIdentity(
    @Value("\${neko.security.trusted-proxy:false}") private val trustedProxy: Boolean,
) {
    fun clientIp(request: HttpServletRequest): String {
        if (trustedProxy) {
            request.getHeader("X-Forwarded-For")
                ?.substringBefore(',')
                ?.let(::normalize)
                ?.let { return it }
            request.getHeader("X-Real-IP")
                ?.let(::normalize)
                ?.let { return it }
        }
        return normalize(request.remoteAddr) ?: "unknown"
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= MAX_ADDRESS_LENGTH && it.none(Char::isISOControl) }

    private companion object {
        const val MAX_ADDRESS_LENGTH = 128
    }
}
