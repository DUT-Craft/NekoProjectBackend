package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.service.TokenStore
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class TokenStoreTest {
    private val redis = mock(StringRedisTemplate::class.java)
    @Suppress("UNCHECKED_CAST")
    private val valueOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
    @Suppress("UNCHECKED_CAST")
    private val setOperations = mock(SetOperations::class.java) as SetOperations<String, String>
    private val store = TokenStore(redis)

    @Test
    fun `user token indexes receive a ttl when tokens are saved`() {
        `when`(redis.opsForValue()).thenReturn(valueOperations)
        `when`(redis.opsForSet()).thenReturn(setOperations)

        val ttl = Duration.ofSeconds(90)
        store.saveAccess("access-jti", 7, "test-agent", "127.0.0.1", ttl)
        store.saveRefresh("refresh-jti", 7, ttl)

        verify(valueOperations).set(
            eq("auth:token:access-jti"),
            startsWith("7|test-agent|127.0.0.1|"),
            eq(ttl),
        )
        verify(valueOperations).set(eq("auth:refresh:refresh-jti"), eq("7"), eq(ttl))
        verify(setOperations).add("auth:user:7:sessions", "access-jti")
        verify(setOperations).add("auth:user:7:refreshes", "refresh-jti")
        verify(redis).expire("auth:user:7:sessions", ttl)
        verify(redis).expire("auth:user:7:refreshes", ttl)
    }
}
