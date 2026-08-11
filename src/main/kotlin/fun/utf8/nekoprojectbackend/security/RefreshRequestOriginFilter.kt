package `fun`.utf8.nekoprojectbackend.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI

/** Rejects cross-site refresh attempts because the refresh credential is carried by a Cookie. */
@Component
class RefreshRequestOriginFilter(
    private val accessDeniedHandler: JsonAccessDeniedHandler,
    @Value("\${neko.cors.allowed-origins}") allowedOrigins: String,
) : OncePerRequestFilter() {
    private val allowedOrigins = allowedOrigins.split(',')
        .map { it.trim().removeSuffix("/") }
        .filter { it.isNotEmpty() }
        .toSet()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || requestPath(request) != REFRESH_PATH

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!hasAllowedBrowserSource(request)) {
            accessDeniedHandler.handle(request, response, AccessDeniedException("刷新请求来源不受信任"))
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun hasAllowedBrowserSource(request: HttpServletRequest): Boolean {
        val fetchSite = request.getHeader("Sec-Fetch-Site")
        if (fetchSite.equals("cross-site", ignoreCase = true)) return false

        val origin = request.getHeader("Origin")?.trim()?.removeSuffix("/")
        if (origin != null) return origin != "null" && origin in allowedOrigins

        val refererOrigin = request.getHeader("Referer")?.let(::originOf)
        return refererOrigin == null || refererOrigin in allowedOrigins
    }

    private fun originOf(value: String): String? = runCatching {
        val uri = URI(value)
        if (uri.scheme == null || uri.host == null) return@runCatching null
        val defaultPort = (uri.scheme.equals("http", true) && uri.port == 80) ||
            (uri.scheme.equals("https", true) && uri.port == 443)
        "${uri.scheme.lowercase()}://${uri.host.lowercase()}" +
            if (uri.port >= 0 && !defaultPort) ":${uri.port}" else ""
    }.getOrNull()

    private fun requestPath(request: HttpServletRequest): String {
        val contextPath = request.contextPath.orEmpty()
        return if (contextPath.isNotEmpty() && request.requestURI.startsWith(contextPath)) {
            request.requestURI.removePrefix(contextPath)
        } else {
            request.requestURI
        }
    }

    private companion object {
        const val REFRESH_PATH = "/api/auth/refresh"
    }
}
