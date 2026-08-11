package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.UserRepository
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
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
        return userRepository.findByUsername(username.trim())
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
    }

    fun findByRole(role: Role): List<User> {
        return userRepository.findByRole(role)
    }

    /** 可归属项目的正常账号：项目管理 + 总管理（总管理也可创建并管理自有项目）。 */
    fun findAssignableOwners(): List<User> {
        return userRepository.findByRoleInAndStatus(ASSIGNABLE_ROLES, Status.ACTIVE)
            .sortedBy { it.username.lowercase() }
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
        val normalizedEmail = normalizeEmail(email)

        validateUsername(normalizedUsername)
        validatePassword(password, normalizedUsername, normalizedEmail)

        if (userRepository.findByUsername(normalizedUsername) != null) {
            throw UserAlreadyExistsException("用户名已存在")
        }
        if (userRepository.findByEmailIgnoreCase(normalizedEmail) != null) {
            throw UserAlreadyExistsException("邮箱已存在")
        }

        return userRepository.save(
            User(
                username = normalizedUsername,
                password = encodePassword(password),
                email = normalizedEmail,
                nickname = normalizedUsername,
                status = Status.ACTIVE,
                role = role,
            )
        )
    }

    fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) {
            throw ParamErrorException("邮箱不能为空")
        }
        if (normalized.length > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matches(normalized)) {
            throw ParamErrorException("邮箱格式不正确")
        }
        return normalized
    }

    fun validatePassword(password: String, username: String? = null, email: String? = null) =
        PasswordPolicy.validate(password, username, email)

    @Transactional
    fun updatePassword(user: User, password: String): User {
        validatePassword(password, user.username, user.email)
        user.password = passwordEncoder.encode(password)
            ?: throw IllegalStateException("Password encoding failed.")
        return userRepository.save(user)
    }

    private fun validateUsername(username: String) {
        if (username.isBlank()) {
            throw ParamErrorException("用户名不能为空")
        }
        if (username.length > MAX_USERNAME_LENGTH) {
            throw ParamErrorException("用户名不能超过 $MAX_USERNAME_LENGTH 个字符")
        }
        if (username.any { it.isWhitespace() || it.isISOControl() }) {
            throw ParamErrorException("用户名不能包含空白或控制字符")
        }
    }

    private fun encodePassword(password: String): String {
        validatePassword(password)
        return passwordEncoder.encode(password)
            ?: throw IllegalStateException("Password encoding failed.")
    }

    private companion object {
        val ASSIGNABLE_ROLES = listOf(Role.PROJECT_MANAGER, Role.SUPER_ADMIN)
        const val MAX_USERNAME_LENGTH = 64
        const val MAX_EMAIL_LENGTH = 128
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
