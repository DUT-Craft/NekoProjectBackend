package top.foxball.nekomainsite.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 验证 JwtService 的签发与自校验闭环（不依赖 Spring 上下文）。 */
class JwtServiceTest {

    private val service = JwtService("test-secret-do-not-use-in-prod")

    @Test
    fun `issue then verify round-trips claims`() {
        val jwt = service.issue(userId = 42L, ttlSeconds = 3600L)

        // JWT 结构：三段，以点分隔
        assertEquals(3, jwt.token.split(".").size)

        val parsed = service.verify(jwt.token)
        assertEquals(42L, parsed?.userId)
        assertEquals(jwt.issuedAt, parsed?.issuedAt)
        assertEquals(jwt.expiresAt, parsed?.expiresAt)
    }

    @Test
    fun `verify rejects wrong secret`() {
        val jwt = JwtService("right-secret").issue(1L, 60L)
        assertNull(JwtService("wrong-secret").verify(jwt.token))
    }

    @Test
    fun `verify rejects tampered payload`() {
        val jwt = service.issue(1L, 60L)
        val parts = jwt.token.split(".").toMutableList()
        parts[1] = parts[1].dropLast(2).plus("AB") // 篡改 payload
        assertNull(service.verify(parts.joinToString(".")))
    }

    @Test
    fun `verify rejects expired token`() {
        val jwt = service.issue(1L, ttlSeconds = -10L)
        assertNull(service.verify(jwt.token))
    }

    @Test
    fun `tokens for different users differ`() {
        val a = service.issue(1L, 60L)
        val b = service.issue(2L, 60L)
        assertNotEquals(a.token, b.token)
    }
}
