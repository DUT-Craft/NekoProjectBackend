package top.foxball.nekomainsite.service.impl

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.authentication.LoginTokenAuthentication
import top.foxball.nekomainsite.handlder.UserDisabledException
import top.foxball.nekomainsite.handlder.UsernameOrPasswordErrorException
import top.foxball.nekomainsite.repository.UserRepository
import top.foxball.nekomainsite.service.AuthService
import top.foxball.nekomainsite.service.AuthUserView

@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val loginTokenAuthentication: LoginTokenAuthentication,
) : AuthService {
    private val unknownUserPasswordHash = passwordEncoder.encode(UNKNOWN_USER_PASSWORD)
        ?: error("password encoder returned null")

    override fun login(
        username: String,
        password: String,
        userAgent: String,
    ): LoginTokenAuthentication.LoginResult {
        // 用户名不存在与密码错误统一回相同消息，避免账号枚举
        val user = userRepository.findByUsername(username)
        if (user == null) {
            passwordEncoder.matches(password, unknownUserPasswordHash)
            throw UsernameOrPasswordErrorException()
        }
        if (!passwordEncoder.matches(password, user.password)) {
            throw UsernameOrPasswordErrorException()
        }
        if (!user.enabled) throw UserDisabledException()
        return loginTokenAuthentication.login(user, userAgent)
    }

    @Transactional(readOnly = true)
    override fun currentUser(userId: Long?): AuthUserView? {
        val user = userId?.let { userRepository.findById(it).orElse(null) }
            ?.takeIf { it.enabled }
            ?: return null
        val persistedId = user.id ?: return null
        return AuthUserView(
            id = persistedId,
            username = user.username,
            displayName = user.displayName ?: user.username,
            role = user.role,
            authSource = user.authSource,
        )
    }

    private companion object {
        const val UNKNOWN_USER_PASSWORD = "not-a-real-user-password"
    }
}
