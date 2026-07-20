package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MemberStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectMemberRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectRole
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.UserRepository
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.UserAlreadyExistsException
import `fun`.utf8.nekoprojectbackend.handlder.UserNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 用户业务：注册（用户名策略 + 邮箱小写归一 + 大小写不敏感查重 + 加密密码）、按用户名/邮箱/ID 查询。 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /** 精确匹配用户名（原样）。如需大小写不敏感，用 [findByUsernameLower]。 */
    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    /** 大小写不敏感查找（按归一化列 [User.usernameLower]）。 */
    fun findByUsernameLower(username: String): User? {
        return userRepository.findByUsernameLower(UsernamePolicy.normalizeKey(username))
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email.trim().lowercase())
    }

    fun findByRole(role: Role): List<User> {
        return userRepository.findByRole(role)
    }

    /** 可归属/创建项目的全部账号：拥有项目创建资格 或 超级管理员（设计 §2.2）。 */
    fun findAssignableOwners(): List<User> {
        return userRepository.findByCanCreateProjectTrueOrRole(canCreateProject = true, role = Role.SUPER_ADMIN)
    }

    /** 当前活跃的超级管理员数量（最后一个超管保护，设计 §11）。 */
    fun countActiveSuperAdmins(): Long =
        userRepository.countByRoleAndStatus(Role.SUPER_ADMIN, Status.ACTIVE)

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
        role: Role = Role.USER,
    ): User {
        val normalizedUsername = UsernamePolicy.validate(username)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || !normalizedEmail.contains('@')) {
            throw ParamErrorException("邮箱格式不正确")
        }

        // 大小写不敏感查重（按归一化列），邮箱同样归一化后查重
        if (userRepository.findByUsernameLower(UsernamePolicy.normalizeKey(normalizedUsername)) != null) {
            throw UserAlreadyExistsException("用户名已存在")
        }
        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw UserAlreadyExistsException("邮箱已存在")
        }

        val user = User().apply {
            this.username = normalizedUsername
            this.password = passwordEncoder.encode(password)
                ?: throw IllegalStateException("Password encoding failed.")
            this.email = normalizedEmail
            this.nickname = normalizedUsername
            this.status = Status.ACTIVE
            this.role = role
        }
        return userRepository.save(user)
    }

    /** 置邮箱已验证时间（注册 / 改邮箱验证成功后调用）。 */
    @Transactional
    fun markEmailVerified(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException() }
        if (user.emailVerifiedAt == null) {
            user.emailVerifiedAt = LocalDateTime.now()
            userRepository.save(user)
        }
    }

    /** 最后一个超级管理员保护（设计 §11）：若目标是当前唯一活跃超管，且操作会使其失权，抛 403。 */
    fun ensureNotLastSuperAdmin(target: User) {
        if (target.role != Role.SUPER_ADMIN || target.status != Status.ACTIVE) return
        if (countActiveSuperAdmins() <= 1) {
            throw ForbiddenException("不能操作最后一个活跃的超级管理员")
        }
    }

    /** 该用户是否为某项目的活跃 OWNER（注销 / 降级前预检，设计 §10.2）。 */
    fun hasActiveOwnedProjects(userId: Long): Boolean =
        projectMemberRepository
            .findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
            .any { it.role == ProjectRole.OWNER }

    /** 注销匿名化（设计 §10.2）：用户名 / 昵称 / 邮箱置为带 id 的占位，保留审计链路但不泄露原值。 */
    @Transactional
    fun anonymize(user: User) {
        val placeholder = "deleted_${user.id}"
        user.username = placeholder
        user.usernameLower = placeholder
        user.nickname = "已注销用户"
        user.email = "$placeholder@deleted.local"
        user.password = "!" // 已注销账号不可通过密码登录（ensureLoginable 也会拦 DELETED）
        userRepository.save(user)
    }
}
