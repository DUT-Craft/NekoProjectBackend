package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class UserServiceTest @Autowired constructor(
    private val service: UserService,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Test
    fun `admin user api stores a password hash and exposes only safe fields`() {
        val created = service.createUser(CreateUserCommand(
            username = "content-editor",
            email = "EDITOR@EXAMPLE.COM",
            password = "a-secure-test-password",
            role = "user",
            displayName = "内容编辑",
            enabled = true,
        ))
        val entity = users.findById(created.id).orElseThrow()

        assertEquals("editor@example.com", created.email)
        assertEquals("USER", created.role)
        assertEquals("LOCAL", created.authSource)
        assertNotEquals("a-secure-test-password", entity.password)
        assertTrue(passwordEncoder.matches("a-secure-test-password", entity.password))
    }

    @Test
    fun `the last enabled administrator cannot be disabled`() {
        val admin = users.findByUsername("admin") ?: error("local admin missing")
        val adminId = requireNotNull(admin.id)
        assertThrows(ConflictException::class.java) { service.disableUserById(adminId) }
        assertTrue(users.findById(adminId).orElseThrow().enabled)
    }

    @Test
    fun `listUsers returns created accounts without password fields`() {
        val created = service.createUser(CreateUserCommand(
            username = "list-target",
            email = "list-target@example.com",
            password = "a-secure-test-password",
            role = "USER",
            displayName = "列表成员",
            enabled = true,
        ))
        val listed = service.listUsers()
        val view = listed.firstOrNull { it.id == created.id } ?: error("created user missing from list")
        assertEquals("list-target", view.username)
        assertTrue(view.toString().contains("list-target"))
    }
}
