package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.JwtProductionConfigurationValidator
import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.config.FileProperties
import `fun`.utf8.nekoprojectbackend.config.TokenCookieProperties
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class JwtProductionConfigurationValidatorTest {

    @Test
    fun `production validator accepts a strong secret and positive ttls`() {
        assertConstructs(
            JwtProperties(
                secret = "01234567890123456789012345678901",
                issuer = "NekoBackend",
                accessTokenTtlSeconds = 7200,
                refreshTokenTtlSeconds = 604800,
            ),
        )
    }

    @Test
    fun `production validator rejects the development secret`() {
        val exception = assertFailsWith<IllegalStateException> {
            JwtProductionConfigurationValidator(
                JwtProperties(secret = "neko-backend-local-dev-secret-2026-change-me"),
                secureCookie(),
                secureFile(),
                "https://project.example.test",
            )
        }

        assertNotNull(exception.message)
    }

    @Test
    fun `production validator rejects a short secret`() {
        assertFailsWith<IllegalStateException> {
            validator(JwtProperties(secret = "too-short"))
        }
    }

    @Test
    fun `production validator rejects invalid token ttls`() {
        assertFailsWith<IllegalStateException> {
            JwtProductionConfigurationValidator(
                JwtProperties(
                    secret = "01234567890123456789012345678901",
                    accessTokenTtlSeconds = 0,
                ),
                secureCookie(),
                secureFile(),
                "https://project.example.test",
            )
        }
    }

    @Test
    fun `production validator accepts case insensitive same site`() {
        assertNull(
            runCatching {
                validator(
                    JwtProperties(secret = "01234567890123456789012345678901"),
                    secureCookie(sameSite = "strict"),
                )
            }.exceptionOrNull(),
        )
    }

    @Test
    fun `production validator rejects non https cors origin`() {
        assertFailsWith<IllegalStateException> {
            validator(
                JwtProperties(secret = "01234567890123456789012345678901"),
                allowedOrigins = "http://project.example.test",
            )
        }
    }

    @Test
    fun `production validator rejects a local or non https file base url`() {
        listOf("http://api.example.test", "https://localhost:8080", "https://api.example.test/files").forEach {
            assertFailsWith<IllegalStateException> {
                validator(
                    JwtProperties(secret = "01234567890123456789012345678901"),
                    fileProps = FileProperties(baseUrl = it),
                )
            }
        }
    }

    private fun assertConstructs(props: JwtProperties) {
        assertNull(runCatching { validator(props) }.exceptionOrNull())
    }

    private fun validator(
        props: JwtProperties,
        cookieProps: TokenCookieProperties = secureCookie(),
        fileProps: FileProperties = secureFile(),
        allowedOrigins: String = "https://project.example.test",
    ) = JwtProductionConfigurationValidator(props, cookieProps, fileProps, allowedOrigins)

    private fun secureCookie(sameSite: String = "Lax") = TokenCookieProperties(
        secure = true,
        httpOnly = true,
        sameSite = sameSite,
    )

    private fun secureFile() = FileProperties(baseUrl = "https://api.example.test")
}
