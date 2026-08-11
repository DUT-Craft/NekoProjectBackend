package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.service.RateLimiter
import `fun`.utf8.nekoprojectbackend.handlder.TooManyRequestsException
import `fun`.utf8.nekoprojectbackend.shared.Response
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/** Bounds password-protected project-management requests before BCrypt verification. */
@Component
class ProjectControlRequestRateLimitFilter(
    private val rateLimiter: RateLimiter,
    private val clientRequestIdentity: ClientRequestIdentity,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !requestPath(request).startsWith(PROJECT_CONTROL_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val clientIp = clientRequestIdentity.clientIp(request)
            rateLimiter.consume("project-control-ip", clientIp, MAX_REQUESTS_PER_IP, WINDOW)

            projectId(requestPath(request))?.let { projectId ->
                rateLimiter.consume(
                    "project-control-project-ip",
                    "$clientIp:$projectId",
                    MAX_REQUESTS_PER_PROJECT_AND_IP,
                    WINDOW,
                )
            }
        } catch (ex: TooManyRequestsException) {
            response.status = ex.code
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(
                objectMapper.writeValueAsString(Response(ex.code, ex.message, emptyMap<String, Any?>())),
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun projectId(path: String): Int? = path
        .removePrefix(PROJECT_CONTROL_PREFIX)
        .substringBefore('/')
        .toIntOrNull()
        ?.takeIf { it > 0 }

    private fun requestPath(request: HttpServletRequest): String {
        val contextPath = request.contextPath.orEmpty()
        return if (contextPath.isNotEmpty() && request.requestURI.startsWith(contextPath)) {
            request.requestURI.removePrefix(contextPath)
        } else {
            request.requestURI
        }
    }

    private companion object {
        const val PROJECT_CONTROL_PREFIX = "/api/admin/project/object-items/"
        const val MAX_REQUESTS_PER_IP = 300
        const val MAX_REQUESTS_PER_PROJECT_AND_IP = 120
        val WINDOW: Duration = Duration.ofHours(1)
    }
}
