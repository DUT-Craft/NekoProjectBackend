package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.AdminUserSeeder
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.service.UserService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import kotlin.test.assertFailsWith

class AdminUserSeederTest {
    private val userService = mock(UserService::class.java)
    private val environment = mock(Environment::class.java)

    @Test
    fun `weak production seed password fails before creating an account`() {
        `when`(environment.acceptsProfiles(any(Profiles::class.java))).thenReturn(true)
        val seeder = seeder(password = "NekoLocalRoot!2026")

        assertFailsWith<IllegalStateException> { seeder.seedAdmin() }

        verifyNoInteractions(userService)
    }

    @Test
    fun `enabled seed does not swallow account creation failures`() {
        `when`(environment.acceptsProfiles(any(Profiles::class.java))).thenReturn(false)
        `when`(userService.findByUsername("admin")).thenReturn(null)
        doThrow(IllegalStateException("database unavailable"))
            .`when`(userService)
            .createUser("admin", "strong-password", "admin@example.test", Role.SUPER_ADMIN)
        val seeder = seeder(password = "strong-password")

        assertFailsWith<IllegalStateException> { seeder.seedAdmin() }
    }

    private fun seeder(password: String) = AdminUserSeeder(
        userService = userService,
        environment = environment,
        enabled = true,
        username = "admin",
        password = password,
        email = "admin@example.test",
    )
}
