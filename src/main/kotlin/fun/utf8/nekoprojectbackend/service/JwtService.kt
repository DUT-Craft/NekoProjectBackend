package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.JwtProperties
import `fun`.utf8.nekoprojectbackend.handlder.TokenExpiredException
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/** JWT 签发与解析（jjwt）：生成 access/refresh token（携带账号等级），解析时校验签名与过期并映射为业务异常。 */
@Service
class JwtService(props: JwtProperties) {

    private val key: SecretKey =
        Keys.hmacShaKeyFor(props.secret.toByteArray(StandardCharsets.UTF_8))
    private val issuer: String = props.issuer

    fun issueAccessToken(userId: Long, username: String, role: String, ttlSeconds: Long): IssuedToken =
        issue(userId, username, role, TYPE_ACCESS, ttlSeconds)

    fun issueRefreshToken(userId: Long, username: String, role: String, ttlSeconds: Long): IssuedToken =
        issue(userId, username, role, TYPE_REFRESH, ttlSeconds)

    private fun issue(userId: Long, username: String, role: String, type: String, ttlSeconds: Long): IssuedToken {
        val jti = UUID.randomUUID().toString()
        val now = Instant.now()
        val token = Jwts.builder()
            .id(jti)
            .issuer(issuer)
            .subject(userId.toString())
            .claim(CLAIM_USERNAME, username)
            .claim(CLAIM_ROLE, role)
            .claim(CLAIM_TYPE, type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact()
        return IssuedToken(token, jti, ttlSeconds)
    }

    /** 解析并校验签名 + 过期；失败抛业务异常 */
    fun parse(token: String): Claims = try {
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (e: ExpiredJwtException) {
        throw TokenExpiredException()
    } catch (e: JwtException) {
        throw TokenInvalidException()
    } catch (e: IllegalArgumentException) {
        throw TokenInvalidException()
    }

    data class IssuedToken(val token: String, val jti: String, val ttlSeconds: Long)

    private companion object {
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
        const val CLAIM_USERNAME = "username"
        const val CLAIM_ROLE = "role"
        const val CLAIM_TYPE = "type"
    }
}
