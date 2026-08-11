package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "neko.local")
data class LocalProperties(
    val admin: Admin = Admin(),
) {
    data class Admin(
        val username: String = "admin",
        val password: String = "admin12345",
        val email: String = "admin@neko.local",
    )
}
