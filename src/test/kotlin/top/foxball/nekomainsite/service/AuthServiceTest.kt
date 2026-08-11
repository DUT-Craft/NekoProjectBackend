package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class AuthServiceTest @Autowired constructor(
    private val service: AuthService,
    private val users: UserRepository,
) {
    @Test
    fun `current user view is returned only for an enabled persisted user`() {
        assertNull(service.currentUser(null))

        val admin = users.findByUsername("admin") ?: error("local admin missing")
        val view = service.currentUser(admin.id)
        assertTrue(view?.authenticated == true)
        assertEquals(admin.username, view?.username)
        assertEquals(admin.displayName ?: admin.username, view?.displayName)

        admin.enabled = false
        users.saveAndFlush(admin)
        assertNull(service.currentUser(admin.id))
    }
}
