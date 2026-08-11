package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "neko.server-status")
data class ServerStatusProperties(
    val enabled: Boolean = false,
    val intervalMs: Long = 60_000,
    val connectTimeoutMs: Int = 3_000,
)
