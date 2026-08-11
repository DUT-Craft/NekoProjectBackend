package `fun`.utf8.nekoprojectbackend.config

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * 应用就绪后确保配置的管理员账户存在。
 *
 * - 账号 / 密码 / 邮箱取自 `neko.admin.*` 配置；
 * - 按用户名判断是否已存在，已存在则跳过（不会覆盖现有密码）；
 * - 以最高优先级执行，先于 [DataSeeder]：管理员账户由本类独占负责，
 *   [DataSeeder] 不再创建 admin 演示用户，故 `neko.seed.enabled=true` 时演示数据可正常播种。
 */
@Component
class AdminUserSeeder(
    private val userService: UserService,
    private val environment: Environment,
    @Value("\${neko.admin.seed-enabled:true}") private val enabled: Boolean,
    @Value("\${neko.admin.username:admin}") private val username: String,
    @Value("\${neko.admin.password:NekoLocalRoot!2026}") private val password: String,
    @Value("\${neko.admin.email:admin@nekobox.local}") private val email: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent::class)
    fun seedAdmin() {
        if (!enabled) {
            log.info("⚠ 跳过初始管理员创建（neko.admin.seed-enabled=false）")
            return
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) &&
            (password == DEFAULT_PASSWORD || password.length < MIN_PRODUCTION_PASSWORD_LENGTH)
        ) {
            throw IllegalStateException(
                "生产环境启用初始管理员创建时，ADMIN_PASSWORD 必须至少 $MIN_PRODUCTION_PASSWORD_LENGTH 位且不能使用默认值",
            )
        }
        if (userService.findByUsername(username) != null) {
            log.info("⚠ 管理员用户 [$username] 已存在，跳过初始化")
            return
        }
        userService.createUser(username = username, password = password, email = email, role = Role.SUPER_ADMIN)
        log.info("✓ 默认管理员用户 [$username] 初始化完成")
    }

    private companion object {
        const val DEFAULT_PASSWORD = "NekoLocalRoot!2026"
        const val MIN_PRODUCTION_PASSWORD_LENGTH = 12
    }
}
