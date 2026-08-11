package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.MailProperties
import `fun`.utf8.nekoprojectbackend.handlder.VerificationCodeInvalidException
import `fun`.utf8.nekoprojectbackend.service.VerificationCodeService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertFailsWith

class VerificationCodeServiceTest {
    private val redis = mock(StringRedisTemplate::class.java)
    @Suppress("UNCHECKED_CAST")
    private val valueOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val service = VerificationCodeService(redis, MailProperties())
    private val context = VerificationCodeService.CodeContext(
        scene = VerificationCodeService.Scene.REGISTER,
        email = "member@example.test",
        userId = null,
        userAgent = "test-agent",
    )

    @Test
    fun `matching verification code is consumed and clears error counter`() {
        `when`(redis.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(anyString())).thenReturn("123456")

        service.verifyAndConsume(context, " 123456 ")

        verify(valueOperations).get(anyString())
        verify(redis, times(2)).delete(anyString())
    }

    @Test
    fun `missing or mismatched verification code is rejected`() {
        `when`(redis.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(anyString())).thenReturn("123456")
        `when`(valueOperations.increment(anyString())).thenReturn(1L)

        assertFailsWith<VerificationCodeInvalidException> {
            service.verifyAndConsume(context, "000000")
        }

        verify(redis, never()).delete(anyString())
    }
}
