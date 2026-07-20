package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.MailProperties
import `fun`.utf8.nekoprojectbackend.handlder.BusinessException
import `fun`.utf8.nekoprojectbackend.handlder.VerificationCodeInvalidException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate

/**
 * 邮箱验证码服务：生成、存储、校验消费、限流。
 *
 * 验证码与「场景 + 用户标识 + UserAgent」绑定，存储于 Redis：
 * - 已登录场景（改密码确认等）：`verify:{scene}:uid:{userId}:ua:{uaHash}`
 * - 匿名场景（注册 / 找回密码）：`verify:{scene}:email:{email}:ua:{uaHash}`（用户未登录，不绑 userId）
 *
 * 限流（防邮件轰炸）：
 * - `verify:lock:email:{email}` —— 两次发送的最小间隔（[MailProperties.sendIntervalSeconds]），TTL 即间隔
 * - `verify:daily:email:{email}:{date}` —— 当日发送次数，TTL 到当天结束
 *
 * 校验通过后原子删除（一次性消费），避免同一验证码被复用。
 */
@Service
class VerificationCodeService(
    private val redis: StringRedisTemplate,
    private val props: MailProperties,
) {

    /** 验证码场景：不同业务用不同 key 前缀，避免一个场景的验证码被另一个场景误用。 */
    enum class Scene(val description: String) {
        REGISTER("注册账号"),
        CHANGE_PASSWORD("修改密码确认"),
        RESET_PASSWORD("找回密码"),
        EMAIL_LOGIN("邮箱验证登录"),
        RECOVER_USERNAME("找回用户名"),
        REACTIVATE("恢复停用账号"),
    }

    /** 存储验证码时的上下文：已登录带 userId，匿名场景 userId 为 null（只用 email 绑定）。 */
    data class CodeContext(
        val scene: Scene,
        val email: String,
        val userId: Long?,
        val userAgent: String,
    )

    /**
     * 生成并存储验证码，返回明文验证码（由上层 MailService 发邮件）。
     * 调用前已做过限流检查（[checkSendLimit]）。
     */
    fun generate(ctx: CodeContext): String {
        val code = randomCode(props.codeLength)
        redis.opsForValue().set(codeKey(ctx), code, Duration.ofSeconds(props.ttlSeconds))
        return code
    }

    /**
     * 校验验证码：匹配则原子删除（一次性消费）；不匹配累计错误次数，超 [MAX_VERIFY_ATTEMPTS] 作废。
     * 不存在或已作废抛 [VerificationCodeInvalidException]（设计 §14.10：错误次数限制 + 原子消费）。
     */
    fun verifyAndConsume(ctx: CodeContext, input: String) {
        val key = codeKey(ctx)
        val stored = redis.opsForValue().get(key)
        if (stored == null) {
            throw VerificationCodeInvalidException()
        }
        if (stored != input.trim()) {
            val errCount = redis.opsForValue().increment(errKey(ctx)) ?: 1L
            redis.expire(errKey(ctx), Duration.ofSeconds(props.ttlSeconds))
            if (errCount >= MAX_VERIFY_ATTEMPTS) {
                // 连续输错超限：作废验证码，防止暴力枚举
                redis.delete(key)
                redis.delete(errKey(ctx))
            }
            throw VerificationCodeInvalidException()
        }
        // 原子消费：校验通过即删除，防止验证码被多次复用
        redis.delete(key)
        redis.delete(errKey(ctx))
    }

    /**
     * 发送前限流检查：
     * - 间隔未到 → 429，提示剩余秒数
     * - 当日超上限 → 429
     * 通过后记录本次发送（占位间隔锁 + 累计当日次数）。
     */
    fun checkAndRecordSend(email: String) {
        val normalizedEmail = email.trim().lowercase()
        val lockKey = lockKey(normalizedEmail)
        val dailyKey = dailyKey(normalizedEmail)

        // 间隔锁：setIfAbsent 成功说明距上次发送已过间隔；失败说明还在冷却期
        val acquired = redis.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(props.sendIntervalSeconds))
        if (acquired != true) {
            val ttl = redis.getExpire(lockKey)
            val wait = if (ttl > 0) ttl else props.sendIntervalSeconds
            throw BusinessException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请 ${wait}s 后再试")
        }

        val count = redis.opsForValue().increment(dailyKey) ?: 1L
        // 每次发送都刷新 TTL 到当日结束（幂等）：避免「increment 成功但 expire 前进程崩溃」
        // 导致 dailyKey 无 TTL 永驻、该邮箱永久超限。即便多次重设也只是延到当天结束，安全。
        redis.expire(dailyKey, Duration.ofSeconds(secondsUntilEndOfDay()))
        if (count > props.dailyLimit) {
            throw BusinessException(HttpStatus.TOO_MANY_REQUESTS, "今日验证码发送次数已达上限（${props.dailyLimit} 次）")
        }
    }

    private fun codeKey(ctx: CodeContext): String {
        val identity = if (ctx.userId != null) "uid:${ctx.userId}" else "email:${ctx.email.trim().lowercase()}"
        return "verify:${ctx.scene}:$identity:ua:${uaHash(ctx.userAgent)}"
    }

    /** 验证码错误次数计数 key（与 [codeKey] 同生命周期，超限作废原码）。 */
    private fun errKey(ctx: CodeContext): String = "${codeKey(ctx)}:err"

    private fun lockKey(email: String) = "verify:lock:email:$email"

    private fun dailyKey(email: String) = "verify:daily:email:$email:${LocalDate.now()}"

    /** UserAgent 取 SHA-256 前 16 位，既可区分终端又避免 key 过长。 */
    private fun uaHash(userAgent: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(userAgent.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun randomCode(length: Int): String {
        val chars = CharArray(length)
        // 用 SecureRandom 生成数字验证码，避免可预测
        val random = java.security.SecureRandom()
        for (i in 0 until length) {
            chars[i] = ('0'.code + random.nextInt(10)).toChar()
        }
        return String(chars)
    }

    private fun secondsUntilEndOfDay(): Long {
        val now = java.time.LocalTime.now()
        val endOfDay = java.time.LocalTime.MAX
        return Duration.between(now, endOfDay).seconds + 1
    }

    private companion object {
        /** 单个验证码连续输错上限，超限作废（设计 §14.10）。 */
        const val MAX_VERIFY_ATTEMPTS = 5
    }
}
