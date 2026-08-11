package top.foxball.nekomainsite.authentication

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 纯逻辑 JWT（HS256，header.payload.signature）签发与校验。
 *
 * 不依赖 Spring、不读写 Redis，仅负责签名/验签；密钥由构造方注入（建议来自配置项，勿硬编码）。
 * 会话元数据落盘与白名单（撤销）查询由调用方配合 [top.foxball.nekomainsite.entity.redis.LoginToken] 完成。
 *
 * 仅承载最小声明集：`sub`(userId)、`iat`、`exp`，时间戳单位均为秒。验签失败 / 结构错误 / 过期一律返回 null。
 */
class JwtService(secret: String) {

    private val hmacKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    private val headerSegment: String

    init {
        val headerJson = """{"alg":"HS256","typ":"JWT"}"""
        headerSegment = base64UrlEncode(headerJson.toByteArray(StandardCharsets.UTF_8))
    }

    /** 签发结果：携带原始 token 与时间戳，便于调用方落盘白名单。 */
    data class IssuedToken(
        val token: String,
        val issuedAt: Instant,
        val expiresAt: Instant,
    )

    /** 解析出的声明；验签失败或过期时为 null。 */
    data class Claims(
        val userId: Long,
        val issuedAt: Instant,
        val expiresAt: Instant,
    )

    /** 签发一张有效期 [ttlSeconds] 秒、绑定 [userId] 的 JWT。 */
    fun issue(userId: Long, ttlSeconds: Long): IssuedToken {
        // iat/exp 约定为秒级，统一截断到秒，使签发与回读的时间戳严格相等
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val exp = now.plusSeconds(ttlSeconds)
        val payload = buildPayload(userId, now, exp)
        val payloadSegment = base64UrlEncode(payload.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerSegment.$payloadSegment"
        val signature = base64UrlEncode(sign(signingInput.toByteArray(StandardCharsets.UTF_8)))
        return IssuedToken(
            token = "$signingInput.$signature",
            issuedAt = now,
            expiresAt = exp,
        )
    }

    /** 验签并校验过期；任一环节失败返回 null，不抛异常外泄内部细节。 */
    fun verify(token: String): Claims? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val signingInput = "${parts[0]}.${parts[1]}"
            // 常量时间比较签名，避免计时侧信道
            val expected = base64UrlEncode(sign(signingInput.toByteArray(StandardCharsets.UTF_8)))
            if (!MessageDigest.isEqual(
                    expected.toByteArray(StandardCharsets.UTF_8),
                    parts[2].toByteArray(StandardCharsets.UTF_8),
                )
            ) return null
            val payload = String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8)
            val claims = parseClaims(payload) ?: return null
            if (Instant.now().isAfter(claims.expiresAt)) return null
            claims
        } catch (e: Exception) {
            null
        }
    }

    private fun sign(input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(input)
    }

    private fun buildPayload(userId: Long, iat: Instant, exp: Instant): String =
        """{"sub":"$userId","iat":${iat.epochSecond},"exp":${exp.epochSecond}}"""

    private fun parseClaims(payload: String): Claims? {
        val sub = SUB_REGEX.find(payload)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val iat = IAT_REGEX.find(payload)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val exp = EXP_REGEX.find(payload)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return Claims(
            userId = sub,
            issuedAt = Instant.ofEpochSecond(iat),
            expiresAt = Instant.ofEpochSecond(exp),
        )
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64UrlDecode(segment: String): ByteArray =
        Base64.getUrlDecoder().decode(segment)

    private companion object {
        val SUB_REGEX = Regex(""""sub"\s*:\s*"?(\d+)"?""")
        val IAT_REGEX = Regex(""""iat"\s*:\s*(\d+)""")
        val EXP_REGEX = Regex(""""exp"\s*:\s*(\d+)""")
    }
}
