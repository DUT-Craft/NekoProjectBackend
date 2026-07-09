package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.shared.Response
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** 过滤器链层 403 响应处理器，输出统一 JSON（[GlobalExceptionHandler] 捕不到过滤器阶段的 403）。 */
@Component
class JsonAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        ex: AccessDeniedException,
    ) {
        val status = HttpStatus.FORBIDDEN.value()
        resp.status = status
        resp.contentType = MediaType.APPLICATION_JSON_VALUE
        resp.characterEncoding = Charsets.UTF_8.name()
        resp.writer.write(
            objectMapper.writeValueAsString(Response(status, ex.message ?: "禁止访问", emptyMap<String, Any?>()))
        )
    }
}
