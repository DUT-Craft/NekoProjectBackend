package top.foxball.nekomainsite.handlder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.HttpRequestMethodNotSupportedException
import top.foxball.nekomainsite.shared.ResponseBuilder

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(ResponseBuilder())

    @Test
    fun `unsupported HTTP method returns 405 in the response and body`() {
        val response = handler.onHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException("PUT", listOf("GET")),
        )

        assertEquals(405, response.statusCode.value())
        assertEquals(405, response.body?.status)
    }

    @Test
    fun `oversized upload returns 413 with a clear message`() {
        val response = handler.onMaxUploadSizeExceededException()

        assertEquals(413, response.statusCode.value())
        assertEquals(413, response.body?.status)
        assertEquals("上传文件过大，请选择不超过 8 MB 的图片", response.body?.message)
    }
}
