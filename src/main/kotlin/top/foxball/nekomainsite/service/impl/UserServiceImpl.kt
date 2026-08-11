package top.foxball.nekomainsite.service.impl

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.repository.UserRepository
import top.foxball.nekomainsite.service.CreateUserCommand
import top.foxball.nekomainsite.service.UpdateUserCommand
import top.foxball.nekomainsite.service.UserAdminView
import top.foxball.nekomainsite.service.UserService
import top.foxball.nekomainsite.shared.requirePresent
import java.time.Instant

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserService {

    @Transactional
    override fun createUser(command: CreateUserCommand): UserAdminView {
        val normalized = normalize(command)
        ensureUnique(normalized.username, normalized.email, null)
        return view(userRepository.save(User(
            username = normalized.username,
            email = normalized.email,
            password = passwordEncoder.encode(normalized.password) ?: error("password encoder returned null"),
            role = normalized.role,
            authSource = "LOCAL",
            displayName = normalized.displayName,
            enabled = normalized.enabled,
        )))
    }

    @Transactional
    override fun createUsers(commands: List<CreateUserCommand>): List<UserAdminView> = commands.map(::createUser)

    override fun getUserById(id: Long): UserAdminView? = userRepository.findById(id).orElse(null)?.let(::view)

    override fun getUsersByIds(ids: List<Long>): List<UserAdminView> = usersInRequestedOrder(ids).map(::view)

    @Transactional
    override fun updateUser(command: UpdateUserCommand): UserAdminView {
        val user = userRepository.findById(command.id).orElseThrow { ResourceNotFoundException("用户不存在") }
        val normalized = normalize(command)
        ensureUnique(normalized.username, normalized.email, user.id)
        ensureAdminRemains(user, normalized.role, normalized.enabled)
        user.username = normalized.username
        user.email = normalized.email
        user.role = normalized.role
        user.displayName = normalized.displayName
        user.enabled = normalized.enabled
        normalized.password?.takeIf(String::isNotBlank)?.let {
            user.password = passwordEncoder.encode(it) ?: error("password encoder returned null")
        }
        user.updatedAt = Instant.now()
        return view(userRepository.save(user))
    }

    @Transactional
    override fun updateUsers(commands: List<UpdateUserCommand>): List<UserAdminView> = commands.map(::updateUser)

    @Transactional
    override fun disableUserById(id: Long): Boolean {
        val user = userRepository.findById(id).orElse(null) ?: return false
        ensureAdminRemains(user, user.role, false)
        user.enabled = false
        user.updatedAt = Instant.now()
        userRepository.save(user)
        return true
    }

    @Transactional
    override fun disableUsersByIds(ids: List<Long>): Boolean {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return true
        val users = usersInRequestedOrder(distinctIds)
        if (users.size != distinctIds.size) return false
        val adminsBeingDisabled = users.count { it.enabled && it.role == "ADMIN" }
        if (adminsBeingDisabled >= userRepository.countByRoleAndEnabledTrue("ADMIN")) {
            throw ConflictException("至少需要保留一个启用的管理员账号")
        }
        val now = Instant.now()
        users.forEach { it.enabled = false; it.updatedAt = now }
        userRepository.saveAll(users)
        return true
    }

    private fun ensureUnique(username: String, email: String, currentId: Long?) {
        userRepository.findByUsername(username)?.takeIf { it.id != currentId }?.let { throw ConflictException("用户名已存在") }
        userRepository.findByEmail(email)?.takeIf { it.id != currentId }?.let { throw ConflictException("邮箱已存在") }
    }

    private fun ensureAdminRemains(current: User, targetRole: String, targetEnabled: Boolean) {
        if (current.enabled && current.role == "ADMIN" && (!targetEnabled || targetRole != "ADMIN") &&
            userRepository.countByRoleAndEnabledTrue("ADMIN") <= 1
        ) throw ConflictException("至少需要保留一个启用的管理员账号")
    }

    private fun normalize(command: CreateUserCommand) = command.copy(
        username = command.username.trim(),
        email = command.email.trim().lowercase(),
        role = normalizeRole(command.role),
        displayName = command.displayName?.trim()?.ifBlank { null },
    )

    private fun normalize(command: UpdateUserCommand) = command.copy(
        username = command.username.trim(),
        email = command.email.trim().lowercase(),
        role = normalizeRole(command.role),
        displayName = command.displayName?.trim()?.ifBlank { null },
    )

    private fun normalizeRole(value: String): String {
        val role = value.trim().uppercase()
        if (role !in ALLOWED_ROLES) throw ConflictException("用户角色不受支持")
        return role
    }

    private fun usersInRequestedOrder(ids: List<Long>): List<User> {
        val usersById = userRepository.findAllById(ids.distinct()).associateBy { it.id }
        return ids.distinct().mapNotNull(usersById::get)
    }

    private fun view(user: User) = UserAdminView(
        user.id.requirePresent("User.id"), user.username, user.email, user.role, user.authSource, user.displayName,
        user.enabled, user.createdAt.toString(), user.updatedAt.toString(),
    )

    private companion object {
        val ALLOWED_ROLES = setOf("USER", "ADMIN")
    }
}
