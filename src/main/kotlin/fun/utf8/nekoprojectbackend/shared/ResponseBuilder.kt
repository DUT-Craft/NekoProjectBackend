package `fun`.utf8.nekoprojectbackend.shared

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class ResponseBuilder {

    fun ok(): Builder = Builder(ResponseCode.OK)

    fun notFound(): Builder = Builder(ResponseCode.NOT_FOUND)

    fun badRequest(): Builder = Builder(ResponseCode.BAD_REQUEST)

    fun forbidden(): Builder = Builder(ResponseCode.FORBIDDEN)

    fun unauthorized(): Builder = Builder(ResponseCode.UNAUTHORIZED)

    fun tooManyRequests(): Builder = Builder(ResponseCode.TOO_MANY_REQUESTS)

    fun exception(): Builder = Builder(ResponseCode.INTERNAL_SERVER_ERROR)

    fun serviceUnavailable(): Builder = Builder(ResponseCode.SERVICE_UNAVAILABLE)

    fun teapot(): Builder = Builder(ResponseCode.IM_A_TEAPOT)

    fun status(status: HttpStatus): Builder = Builder(status)

    inner class Builder {
        private var status: Int

        private var defaultMessage: String

        private var customMessage: String? = null

        private var data: Any? = null

        private val headers = HttpHeaders()

        constructor(responseCode: ResponseCode) {
            this.status = responseCode.code
            this.defaultMessage = responseCode.message
        }

        constructor(httpStatus: HttpStatus) {
            this.status = httpStatus.value()
            this.defaultMessage = httpStatus.reasonPhrase
        }

        fun message(message: String?) = apply {
            this.customMessage = message
        }

        fun data(data: Any?) = apply {
            this.data = data
        }

        fun header(key: String, value: String) = apply {
            this.headers.add(key, value)
        }

        fun headers(httpHeaders: HttpHeaders) = apply {
            this.headers.addAll(httpHeaders)
        }

        fun retryAfter(seconds: Long) = apply {
            this.headers.add("Retry-After", seconds.toString())
        }

        fun build(): ResponseEntity<Response> {
            val finalData = this.data ?: HashMap<String, Any?>()

            val responseBody = Response(
                status = this.status,
                message = this.customMessage ?: this.defaultMessage,
                data = finalData
            )

            return ResponseEntity
                .status(this.status)
                .headers(this.headers)
                .body(responseBody)
        }
    }

    enum class ResponseCode(val code: Int, val message: String) {
        OK(200, "OK"),
        NOT_FOUND(404, "Not Found"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        BAD_REQUEST(400, "Bad Request"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
        IM_A_TEAPOT(418, "I'm a teapot"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    }
}
