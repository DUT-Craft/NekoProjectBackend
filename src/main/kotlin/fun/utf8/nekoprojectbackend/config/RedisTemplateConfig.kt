package `fun`.utf8.nekoprojectbackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer

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
