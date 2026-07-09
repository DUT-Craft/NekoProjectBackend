package `fun`.utf8.nekoprojectbackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** 注册全局密码编码器（BCrypt），供用户口令与项目控制密码的加密/校验使用。 */
@Configuration
class SecurityEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}