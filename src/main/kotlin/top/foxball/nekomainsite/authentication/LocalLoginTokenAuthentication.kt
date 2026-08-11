package top.foxball.nekomainsite.authentication

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import top.foxball.nekomainsite.config.JwtProperties
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.entity.redis.LoginToken
import top.foxball.nekomainsite.shared.requirePresent
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
@Profile("local")
class LocalLoginTokenAuthentication(
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
) : LoginTokenAuthentication {
    private val tokens = ConcurrentHashMap<String, LoginToken>()
    private val expirations = ConcurrentHashMap<String, Instant>()

    override fun isValid(userId: Long, token: String, userAgent: String): Boolean {
        val record = tokens[token] ?: return false
        val expiresAt = expirations[token]
        if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            deleteToken(token)
            return false
        }
        return record.userId == userId && record.userAgent == userAgent
    }

    override fun createToken(userId: Long, userAgent: String): LoginToken {
        val issued = jwtService.issue(userId, jwtProperties.ttlSeconds)
        return LoginToken(issued.token, userId, userAgent, jwtProperties.ttlSeconds).also {
            tokens[issued.token] = it
            expirations[issued.token] = issued.expiresAt
        }
    }

    override fun deleteToken(token: String) {
        tokens.remove(token)
        expirations.remove(token)
    }

    override fun deleteToken(token: LoginToken) {
        token.id?.let(::deleteToken)
    }

    override fun findAll(userId: Long): MutableList<LoginToken?>? = tokens.values.filter { it.userId == userId }.toMutableList()

    override fun login(user: User, userAgent: String): LoginTokenAuthentication.LoginResult {
        val userId = user.id.requirePresent("User.id")
        val token = createToken(userId, userAgent).id.requirePresent("LoginToken.id")
        return LoginTokenAuthentication.LoginResult(
            state = LoginTokenAuthentication.LoginResult.State.SUCCESS,
            response = LoginTokenAuthentication.LoginResult.Response(token = token, userId = userId),
        )
    }
}
