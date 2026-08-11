package top.foxball.nekomainsite

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.nekomainsite.repository.LoginTokenRepository
import top.foxball.nekomainsite.repository.UserRepository

@ActiveProfiles("prod")
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:prod-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "neko.security.jwt.secret=production-context-test-secret-32-characters-long",
    "neko.security.jwt.cookie-secure=true",
    "neko.security.cors.allowed-origin-patterns=https://www.neko-mc.example",
    "neko.file.base-url=https://api.neko-mc.example",
    "neko.file.storage-path=\${java.io.tmpdir}/neko-production-context-test",
    "neko.bootstrap-admin.username=production-admin",
    "neko.bootstrap-admin.password=production-admin-password",
    "neko.bootstrap-admin.email=admin@neko-mc.example",
    "neko.server-status.enabled=false",
    "management.health.redis.enabled=false",
])
class ProductionContextTest {
    @MockitoBean(name = "redisConnectionFactory")
    lateinit var redisConnectionFactory: LettuceConnectionFactory

    @Autowired
    lateinit var redisTemplate: RedisTemplate<*, *>

    @Autowired
    lateinit var loginTokenRepository: LoginTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `production context uses standard redis infrastructure without unsafe json typing`() {
        assertNotNull(loginTokenRepository)
        assertFalse(redisTemplate.defaultSerializer is GenericJacksonJsonRedisSerializer)
        assertTrue(userRepository.findByUsername("production-admin")?.role == "ADMIN")
    }
}
