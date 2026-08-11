package top.foxball.nekomainsite.config

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.repository.UserRepository

@ConfigurationProperties(prefix = "neko.bootstrap-admin")
data class ProductionAdminProperties(
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val displayName: String = "站点管理员",
)

@Component
@Profile("prod")
class ProductionAdminBootstrap(
    private val properties: ProductionAdminProperties,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
) : CommandLineRunner {
    @Transactional
    override fun run(vararg args: String) {
        if (userRepository.countByRoleAndEnabledTrue("ADMIN") > 0) return

        val username = properties.username.trim()
        val password = properties.password
        val email = properties.email.trim()
        val displayName = properties.displayName.trim().ifBlank { "站点管理员" }

        require(USERNAME.matches(username)) {
            "BOOTSTRAP_ADMIN_USERNAME is required on first production startup and must be 3-50 letters, numbers, dots, underscores or hyphens"
        }
        require(password.length >= 12) {
            "BOOTSTRAP_ADMIN_PASSWORD is required on first production startup and must contain at least 12 characters"
        }
        require(email.length <= 100 && EMAIL.matches(email)) {
            "BOOTSTRAP_ADMIN_EMAIL is required on first production startup and must be a valid email address"
        }
        require(displayName.length <= 80) { "BOOTSTRAP_ADMIN_DISPLAY_NAME must not exceed 80 characters" }
        require(!userRepository.existsByUsername(username)) { "BOOTSTRAP_ADMIN_USERNAME is already used by a non-admin account" }
        require(!userRepository.existsByEmail(email)) { "BOOTSTRAP_ADMIN_EMAIL is already used by a non-admin account" }

        userRepository.save(User(
            username = username,
            email = email,
            password = passwordEncoder.encode(password) ?: error("password encoder returned null"),
            role = "ADMIN",
            displayName = displayName,
        ))
    }

    private companion object {
        val USERNAME = Regex("[A-Za-z0-9_.-]{3,50}")
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
