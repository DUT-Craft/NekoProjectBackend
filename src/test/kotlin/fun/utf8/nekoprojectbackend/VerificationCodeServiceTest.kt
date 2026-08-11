package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.MailProperties
import `fun`.utf8.nekoprojectbackend.handlder.VerificationCodeInvalidException
import `fun`.utf8.nekoprojectbackend.service.VerificationCodeService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import kotlin.test.assertFailsWith

class VerificationCodeServiceTest {
    private val redis = mock(StringRedisTemplate::class.java)
    private val service = VerificationCodeService(redis, MailProperties())
    private val context = VerificationCodeService.CodeContext(
        scene = VerificationCodeService.Scene.REGISTER,
        email = "member@example.test",
        userId = null,
        userAgent = "test-agent",
    )

    @Test
    fun `matching verification code is consumed through one atomic redis operation`() {
        `when`(
            redis.execute(
                any<RedisScript<Long>>(),
                anyList(),
                eq("123456"),
                eq("5"),
            ),
        ).thenReturn(1L)

        service.verifyAndConsume(context, " 123456 ")

        verify(redis).execute(
            any<RedisScript<Long>>(),
            anyList(),
            eq("123456"),
            eq("5"),
        )
        verify(redis, never()).delete(any<String>())
    }

    @Test
    fun `missing or mismatched verification code is rejected`() {
        `when`(
            redis.execute(
                any<RedisScript<Long>>(),
                anyList(),
                eq("000000"),
                eq("5"),
            ),
        ).thenReturn(0L)

        assertFailsWith<VerificationCodeInvalidException> {
            service.verifyAndConsume(context, "000000")
        }
    }
}
