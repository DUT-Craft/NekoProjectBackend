package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.User
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.UserRepository
import `fun`.utf8.nekoprojectbackend.handlder.UserAlreadyExistsException
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

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

    fun save(user: User): User {
        return userRepository.save(user)
    }

    fun findById(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }

    @Transactional
    fun createUser(username: String, password: String, email: String): User {
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
            )
        )
    }
}
