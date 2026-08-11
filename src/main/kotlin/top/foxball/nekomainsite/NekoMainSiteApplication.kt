package top.foxball.nekomainsite

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import top.foxball.nekomainsite.config.CorsProperties
import top.foxball.nekomainsite.config.BlessingSkinProperties
import top.foxball.nekomainsite.config.FileProperties
import top.foxball.nekomainsite.config.LocalProperties
import top.foxball.nekomainsite.config.ProductionAdminProperties
import top.foxball.nekomainsite.config.ServerStatusProperties

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableScheduling
@EnableConfigurationProperties(
    CorsProperties::class,
    BlessingSkinProperties::class,
    FileProperties::class,
    LocalProperties::class,
    ProductionAdminProperties::class,
    ServerStatusProperties::class,
)
class NekoMainSiteApplication

fun main(args: Array<String>) {
    runApplication<NekoMainSiteApplication>(*args)
}
