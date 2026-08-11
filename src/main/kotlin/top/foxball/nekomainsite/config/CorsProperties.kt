package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "neko.security.cors")
data class CorsProperties(
    val allowedOriginPatterns: List<String> = listOf(
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3001",
        "http://localhost:3100",
        "http://127.0.0.1:3100",
        "http://localhost:3101",
        "http://127.0.0.1:3101",
    ),
)
