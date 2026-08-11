package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.service.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceValidationTests @Autowired constructor(
    private val userService: UserService,
) {
    @Test
    fun `user creation validates account fields in the service layer`() {
        assertFailsWith<ParamErrorException> {
            userService.createUser("", "Strong!234", "member@example.test")
        }
        assertFailsWith<ParamErrorException> {
            userService.createUser("member", "12345", "member@example.test")
        }
        assertFailsWith<ParamErrorException> {
            userService.createUser("member", "Strong!234", "not-an-email")
        }
        assertFailsWith<ParamErrorException> {
            userService.createUser("member", "猫".repeat(25), "member@example.test")
        }
    }

    @Test
    fun `email is normalized and looked up without case sensitivity`() {
        val created = userService.createUser(
            username = "case-email-user",
            password = "Strong!234",
            email = " Case.User@Example.Test ",
        )

        assertEquals("case.user@example.test", created.email)
        assertEquals(created.id, userService.findByEmail("CASE.USER@example.test")?.id)
    }
}
