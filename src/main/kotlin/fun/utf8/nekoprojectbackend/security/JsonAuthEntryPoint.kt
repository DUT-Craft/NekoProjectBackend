package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.handlder.BusinessException
import `fun`.utf8.nekoprojectbackend.shared.Response
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Security 过滤器链层抛出的鉴权异常（发生在 DispatcherServlet 之前），
 * GlobalExceptionHandler 捕不到，故在此统一输出与业务接口一致的 JSON。
 */
@Component
class JsonAuthEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        ex: AuthenticationException,
    ) {
        val cause = req.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR) as? BusinessException
        val status = cause?.code ?: HttpStatus.UNAUTHORIZED.value()
        val message = cause?.message ?: "未授权"
        write(resp, status, message)
    }

    private fun write(resp: HttpServletResponse, status: Int, message: String) {
        resp.status = status
        resp.contentType = MediaType.APPLICATION_JSON_VALUE
        resp.characterEncoding = Charsets.UTF_8.name()
        resp.writer.write(
            objectMapper.writeValueAsString(Response(status, message, emptyMap<String, Any?>()))
        )
    }
}
