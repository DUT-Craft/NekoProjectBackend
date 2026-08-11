package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.foxball.nekomainsite.authentication.JwtService

/** 装配 [JwtService]（密钥来自 [JwtProperties]，可被环境变量覆盖）。 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {
    @Bean
    fun jwtService(properties: JwtProperties): JwtService = JwtService(properties.secret)
}
