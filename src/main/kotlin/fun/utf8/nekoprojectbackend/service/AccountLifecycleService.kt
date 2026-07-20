package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.UserNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 账号生命周期业务（设计 §10）：用户自助停用 / 恢复 / 注销。
 *
 * - 停用：DEACTIVATED + 吊销全部会话；冷静期内可恢复；
 * - 注销：预检「无活跃 OWNER 项目」→ DELETED + 匿名化 + 吊销全部会话；
 * - 敏感操作（注销）要求重新输入当前密码确认（设计 §11 / §6.4）。
 */
@Service
class AccountLifecycleService(
    private val userService: UserService,
    private val tokenStore: TokenStore,
    private val passwordEncoder: PasswordEncoder,
) {

    /** 用户自助停用：DEACTIVATED，吊销全部会话。冷静期内可凭邮箱验证码恢复（见 AuthService.reactivateByEmail）。 */
    @Transactional
    fun deactivate(userId: Long) {
        val user = load(userId)
        userService.ensureNotLastSuperAdmin(user)
        user.status = Status.DEACTIVATED
        user.deactivatedAt = LocalDateTime.now()
        userService.save(user)
        tokenStore.invalidateAllSessions(userId)
    }

    /** 用户注销：校验密码 + 预检无活跃 OWNER 项目 → DELETED + 匿名化 + 吊销全部会话。 */
    @Transactional
    fun delete(userId: Long, confirmPassword: String) {
        val user = load(userId)
        if (!passwordEncoder.matches(confirmPassword, user.password)) {
            throw ParamErrorException("密码不正确")
        }
        userService.ensureNotLastSuperAdmin(user)
        if (userService.hasActiveOwnedProjects(userId)) {
            throw ParamErrorException("你仍是部分项目的拥有者，请先转交、归档或删除这些项目后再注销")
        }
        user.status = Status.DELETED
        user.deletedAt = LocalDateTime.now()
        userService.save(user)
        userService.anonymize(user)
        tokenStore.invalidateAllSessions(userId)
    }

    private fun load(userId: Long): User =
        userService.findById(userId) ?: throw UserNotFoundException()
}
