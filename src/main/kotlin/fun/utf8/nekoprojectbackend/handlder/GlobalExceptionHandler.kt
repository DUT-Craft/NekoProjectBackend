package `fun`.utf8.nekoprojectbackend.handlder

import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException


@Order(2)
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val builder = ResponseBuilder()

    @ExceptionHandler(BusinessException::class)
    fun onBusinessException(ex: BusinessException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .build()
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun onAccessDeniedException(ex: AccessDeniedException): ResponseEntity<Response> {
        return builder.forbidden()
            .message(ex.message ?: "禁止访问")
            .build()
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onHttpRequestMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Method \"${ex.method}\" is not supported on this endpoint.")
            .build()
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun onNoResourceOrHandlerFoundException(): ResponseEntity<Response> {
        return builder.notFound().build()
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun onMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required parameter \"${ex.parameterName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun onMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required request header \"${ex.headerName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Parameter \"${ex.parameter.parameterName}\" type mismatch. Expected ${ex.requiredType}.")
            .build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun onIllegalArgumentException(ex: IllegalArgumentException?): ResponseEntity<Response> {
        log.warn("Illegal argument access happened: ", ex)
        return builder.badRequest()
            .message(ex?.message ?: "Invalid argument.")
            .build()
    }

    @ExceptionHandler(Exception::class)
    fun onException(req: HttpServletRequest, ex: Exception?): ResponseEntity<Response> {
        log.error("Got an exception while process request: {}", req.requestURI, ex)
        return builder.exception().build()
    }
}
