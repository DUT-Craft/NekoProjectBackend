package top.foxball.nekomainsite.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.nekomainsite.authentication.LoginTokenAuthentication
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.handlder.UserDisabledException
import top.foxball.nekomainsite.handlder.UsernameOrPasswordErrorException
import top.foxball.nekomainsite.repository.UserRepository

class AuthServiceImplTest {
    @Test
    fun `unknown usernames still perform one password verification`() {
        val users = Mockito.mock(UserRepository::class.java)
        Mockito.`when`(users.findByUsername("missing-user")).thenReturn(null)
        val encoder = RecordingPasswordEncoder()
        val service = AuthServiceImpl(
            users,
            encoder,
            Mockito.mock(LoginTokenAuthentication::class.java),
        )

        assertThrows(UsernameOrPasswordErrorException::class.java) {
            service.login("missing-user", "attempted-password", "test-agent")
        }

        assertEquals(listOf("attempted-password"), encoder.verifiedPasswords)
    }

    @Test
    fun `disabled accounts verify an incorrect password before revealing account status`() {
        val users = Mockito.mock(UserRepository::class.java)
        Mockito.`when`(users.findByUsername("disabled-user")).thenReturn(User(
            username = "disabled-user",
            email = "disabled@example.com",
            password = "stored-password-hash",
            enabled = false,
        ))
        val encoder = RecordingPasswordEncoder()
        val service = AuthServiceImpl(
            users,
            encoder,
            Mockito.mock(LoginTokenAuthentication::class.java),
        )

        assertThrows(UsernameOrPasswordErrorException::class.java) {
            service.login("disabled-user", "incorrect-password", "test-agent")
        }

        assertEquals(listOf("incorrect-password"), encoder.verifiedPasswords)
    }

    @Test
    fun `disabled accounts still return their disabled status after a correct password`() {
        val users = Mockito.mock(UserRepository::class.java)
        Mockito.`when`(users.findByUsername("disabled-user")).thenReturn(User(
            username = "disabled-user",
            email = "disabled@example.com",
            password = "stored-password-hash",
            enabled = false,
        ))
        val encoder = RecordingPasswordEncoder(matchesResult = true)
        val service = AuthServiceImpl(
            users,
            encoder,
            Mockito.mock(LoginTokenAuthentication::class.java),
        )

        assertThrows(UserDisabledException::class.java) {
            service.login("disabled-user", "correct-password", "test-agent")
        }

        assertEquals(listOf("correct-password"), encoder.verifiedPasswords)
    }

    private class RecordingPasswordEncoder(
        private val matchesResult: Boolean = false,
    ) : PasswordEncoder {
        val verifiedPasswords = mutableListOf<String>()

        override fun encode(rawPassword: CharSequence?): String? = "encoded-placeholder"

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            verifiedPasswords += rawPassword?.toString().orEmpty()
            return matchesResult
        }
    }
}
