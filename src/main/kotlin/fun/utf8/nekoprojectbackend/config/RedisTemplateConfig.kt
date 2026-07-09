package `fun`.utf8.nekoprojectbackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer

/**
 * 默认 RedisTemplate：key 与 value 均按 JSON 序列化（含非安全默认类型）。
 * 注意：key 也会被序列化为带引号的 JSON 串，按前缀（如 KEYS auth:token:*）扫描无法命中；
 * token 白名单等需要可读 key 的场景请改用 StringRedisTemplate。
 */
@Configuration
class RedisTemplateConfig {
    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory?): RedisTemplate<*, *> {

        val redisTemplate: RedisTemplate<*, *> = RedisTemplate<Any?, Any?>()
        redisTemplate.connectionFactory = redisConnectionFactory

        val jsonRedisSerializer = GenericJacksonJsonRedisSerializer.builder()
            .enableUnsafeDefaultTyping()
            .build()
        redisTemplate.defaultSerializer = jsonRedisSerializer //设置默认的Serialize，包含 keySerializer & valueSerializer

        return redisTemplate
    }
}
