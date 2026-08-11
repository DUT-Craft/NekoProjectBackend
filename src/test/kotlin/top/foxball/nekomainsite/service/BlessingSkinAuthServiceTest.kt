package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.handlder.UserDisabledException
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class BlessingSkinAuthServiceTest @Autowired constructor(
    private val service: BlessingSkinAuthService,
    private val users: UserRepository,
) {
    @Test
    fun `external account collisions resolve to stable unique identifiers`() {
        users.save(User(
            username = "blessing_collision",
            email = "taken@example.com",
            password = "not-used",
        ))

        val created = service.upsertUser("collision", "碰撞测试", "taken@example.com")
        assertNotEquals("blessing_collision", created.username)
        assertNotEquals("taken@example.com", created.email)

        val restored = service.upsertUser("collision", "更新名称", "taken@example.com")
        assertEquals(created.id, restored.id)
        assertEquals(created.username, restored.username)
        assertEquals(created.email, restored.email)
    }

    @Test
    fun `disabled external account cannot obtain a new session user`() {
        users.save(User(
            username = "disabled_external",
            email = "disabled-external@example.com",
            password = "not-used",
            authSource = "BLESSING_SKIN",
            externalUserId = "disabled-external-id",
            enabled = false,
        ))

        assertThrows(UserDisabledException::class.java) {
            service.upsertUser("disabled-external-id", "禁用用户", "disabled-external@example.com")
        }
    }
}
