package `fun`.utf8.nekoprojectbackend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

/** 应用启动时按配置清空 Redis；默认关闭，仅供显式启用的本地数据重置。 */
@Component
class RedisCleanupConfig(
    private val redisTemplate: StringRedisTemplate,
    @Value($$"${redis.clear-on-startup:false}")
    private val clearOnStartup: Boolean
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun clearRedisOnStartup() {
        if (!clearOnStartup) {
            return
        }

        try {
            redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
            logger.info("Redis database cleared because redis.clear-on-startup is enabled")
        } catch (e: Exception) {
            logger.error("Failed to clear Redis database", e)
        }
    }
}
