package `fun`.utf8.nekoprojectbackend.handlder

import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException


/** 全局异常处理：将各类异常转换为统一 [Response] 响应。 */
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
    fun onAccessDeniedException(): ResponseEntity<Response> {
        return builder.forbidden()
            .message("禁止访问")
            .build()
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onHttpRequestMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException): ResponseEntity<Response> {
        return builder.status(HttpStatus.METHOD_NOT_ALLOWED)
            .message("请求方法 ${ex.method} 不受支持")
            .build()
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onHttpMessageNotReadableException(): ResponseEntity<Response> {
        return builder.badRequest()
            .message("请求体格式错误")
            .build()
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun onMissingServletRequestPartException(ex: MissingServletRequestPartException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required request part \"${ex.requestPartName}\" is not provided!")
            .build()
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun onHttpMediaTypeNotSupportedException(): ResponseEntity<Response> {
        return builder.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .message("不支持的请求内容类型")
            .build()
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun onMaxUploadSizeExceededException(): ResponseEntity<Response> {
        return builder.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .message("上传文件超过大小限制")
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
            .message("参数 \"${ex.parameter.parameterName}\" 格式错误")
            .build()
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<Response> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "请求参数校验失败"
        return builder.badRequest()
            .message(message)
            .build()
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun onDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<Response> {
        log.warn("Data integrity conflict while processing request", ex)
        return builder.status(HttpStatus.CONFLICT)
            .message("数据冲突，请检查是否重复提交")
            .build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun onIllegalArgumentException(ex: IllegalArgumentException?): ResponseEntity<Response> {
        log.warn("Illegal argument access happened: ", ex)
        return builder.badRequest()
            .message("请求参数不合法")
            .build()
    }

    @ExceptionHandler(Exception::class)
    fun onException(req: HttpServletRequest, ex: Exception?): ResponseEntity<Response> {
        log.error("Got an exception while process request: {}", req.requestURI, ex)
        return builder.exception().build()
    }
}
