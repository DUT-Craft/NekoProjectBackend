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
import org.springframework.stereotype.Component

/**
 * 应用就绪后确保配置的管理员账户存在。
 *
 * - 账号 / 密码 / 邮箱取自 `neko.admin.*` 配置；
 * - 按用户名判断是否已存在，已存在则跳过（不会覆盖现有密码）；
 * - 以最高优先级执行，先于 [DataSeeder]：管理员账户由本类独占负责，
 *   [DataSeeder] 不再创建 admin 演示用户，故 `neko.seed.enabled=true` 时演示数据可正常播种。
 *
 * 弱口令守卫（设计 §14 修正）：prod profile 下拒绝用空口令或弱默认值（password）初始化超管，
 * 避免 prod 误配后留下 admin/password 后门；dev/test 仍允许默认口令以便本地开发。
 */
@Component
class AdminUserSeeder(
    private val userService: UserService,
    private val env: Environment,
    @Value("\${neko.admin.seed-enabled:true}") private val enabled: Boolean,
    @Value("\${neko.admin.username:admin}") private val username: String,
    @Value("\${neko.admin.password:password}") private val password: String,
    @Value("\${neko.admin.email:admin@nekobox.local}") private val email: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent::class)
    fun seedAdmin() {
        if (!enabled) {
            log.info("⚠ 跳过管理员初始化（neko.admin.seed-enabled=false）")
            return
        }
        val isProd = env.activeProfiles.any { it.equals("prod", ignoreCase = true) }
        if (isProd && isWeakProductionPassword(password)) {
            throw IllegalStateException(
                "生产环境 neko.admin.password 为空或使用默认弱口令，请通过 NEKO_ADMIN_PASSWORD 设置强口令",
            )
        }
        if (userService.findByUsername(username) != null) {
            log.info("⚠ 管理员用户 [$username] 已存在，跳过初始化")
            return
        }
        if (isProd) {
            log.info("⚠ prod 环境正在用配置口令初始化超管 [{}]（请确认口令已通过环境变量安全设置）", username)
        }
        try {
            val user = userService.createUser(username = username, password = password, email = email, role = Role.SUPER_ADMIN)
            // 管理员账号视为已验证邮箱，否则 ensureLoginable 会拦截登录（设计 §3.2）
            userService.markEmailVerified(user.id!!)
            log.info("✓ 默认管理员用户 [$username] 初始化完成")
        } catch (e: Exception) {
            log.error("✗ 默认管理员用户 [$username] 初始化失败: ${e.message}", e)
            throw e
        }
    }

    private fun isWeakProductionPassword(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isBlank() || WEAK_DEFAULT_PASSWORDS.any { it.equals(normalized, ignoreCase = true) }
    }

    private companion object {
        // 与本地/历史默认值一致，用于生产弱默认口令守卫比对。
        val WEAK_DEFAULT_PASSWORDS = setOf("password", "NekoLocalRoot!2026")
    }
}
