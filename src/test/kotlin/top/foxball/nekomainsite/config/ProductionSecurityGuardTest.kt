package top.foxball.nekomainsite.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProductionSecurityGuardTest {
    @Test
    fun `explicit https production origins are accepted`() {
        assertDoesNotThrow { guard(listOf("https://www.neko-mc.example")).validate() }
    }

    @Test
    fun `wildcard and insecure production origins are rejected`() {
        listOf(
            listOf("*"),
            listOf("https://*.neko-mc.example"),
            listOf("http://www.neko-mc.example"),
            listOf("https://www.neko-mc.example", "http://localhost:3100"),
            listOf("https://user@example.com"),
            listOf("https://www.neko-mc.example/path"),
            listOf("https://www.neko-mc.example?source=test"),
            listOf("https://www.neko-mc.example#fragment"),
        ).forEach { origins ->
            assertThrows(IllegalArgumentException::class.java) { guard(origins).validate() }
        }
    }

    @Test
    fun `production media configuration requires an exact https origin and absolute storage path`() {
        assertThrows(IllegalArgumentException::class.java) {
            guard(fileProperties = FileProperties(storagePath = "relative/storage", baseUrl = "https://api.neko-mc.example")).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            guard(fileProperties = FileProperties(storagePath = storagePath, baseUrl = "https://api.neko-mc.example/media")).validate()
        }
    }

    @Test
    fun `enabled Blessing Skin requires complete HTTPS configuration`() {
        val incomplete = BlessingSkinProperties(
            enabled = true,
            authorizationUrl = "https://skin.example/oauth/authorize",
            tokenUrl = "https://skin.example/oauth/token",
            userInfoUrl = "https://skin.example/api/user",
            clientId = "client",
            clientSecret = "",
            redirectUri = "https://mc.example/api/auth/blessing/callback",
        )

        assertThrows(IllegalArgumentException::class.java) {
            guard(listOf("https://www.neko-mc.example"), incomplete).validate()
        }
    }

    private fun guard(
        origins: List<String> = listOf("https://www.neko-mc.example"),
        blessingSkin: BlessingSkinProperties = BlessingSkinProperties(),
        fileProperties: FileProperties = FileProperties(storagePath = storagePath, baseUrl = "https://api.neko-mc.example"),
    ) = ProductionSecurityGuard(
        JwtProperties(secret = "production-test-secret-at-least-32-characters", cookieSecure = true),
        CorsProperties(allowedOriginPatterns = origins),
        fileProperties,
        blessingSkin,
    )

    private companion object {
        val storagePath: String = Path.of(System.getProperty("java.io.tmpdir"), "neko-production-guard-test").toAbsolutePath().toString()
    }
}
