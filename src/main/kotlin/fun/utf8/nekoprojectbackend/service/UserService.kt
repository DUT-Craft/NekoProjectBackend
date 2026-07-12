package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.UserRepository
import `fun`.utf8.nekoprojectbackend.handlder.UserAlreadyExistsException
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/** 用户业务：注册（校验用户名/邮箱唯一并加密密码）、按用户名/邮箱/ID 查询。 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    fun findByRole(role: Role): List<User> {
        return userRepository.findByRole(role)
    }

    /** 可归属项目的全部账号：项目管理 + 总管理（总管理也可创建并管理自有项目）。 */
    fun findAssignableOwners(): List<User> {
        return userRepository.findByRoleIn(listOf(Role.PROJECT_MANAGER, Role.SUPER_ADMIN))
    }

    /** 批量取用户名（供邀请码历史等场景把用户 ID 解析为可读名称）。 */
    fun namesByIds(ids: Collection<Long>): Map<Long, String> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return userRepository.findAllById(ids).associate { it.id!! to it.username }
    }

    fun save(user: User): User {
        return userRepository.save(user)
    }

    fun findById(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }

    @Transactional
    fun createUser(
        username: String,
        password: String,
        email: String,
        role: Role = Role.PROJECT_MANAGER,
    ): User {
        val normalizedUsername = username.trim()
        val normalizedEmail = email.trim()

        if (userRepository.findByUsername(normalizedUsername) != null) {
            throw UserAlreadyExistsException("用户名已存在")
        }
        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw UserAlreadyExistsException("邮箱已存在")
        }

        return userRepository.save(
            User(
                username = normalizedUsername,
                password = passwordEncoder.encode(password)
                    ?: throw IllegalStateException("Password encoding failed."),
                email = normalizedEmail,
                nickname = normalizedUsername,
                status = Status.ACTIVE,
                role = role,
            )
        )
    }
}
