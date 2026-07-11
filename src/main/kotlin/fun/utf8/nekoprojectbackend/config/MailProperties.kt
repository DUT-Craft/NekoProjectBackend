package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 邮件验证码配置（neko.mail.verification.*）。
 *
 * - [ttlSeconds] 验证码有效期（默认 600s = 10 分钟）
 * - [codeLength] 验证码位数（默认 6 位纯数字）
 * - [sendIntervalSeconds] 同一邮箱两次发送的最小间隔（防轰炸，默认 60s）
 * - [dailyLimit] 同一邮箱每日发送上限（默认 10 次）
 * - [from] 发件人地址，默认回退到 spring.mail.username
 * - [subjectPrefix] 邮件主题前缀
 */
@ConfigurationProperties(prefix = "neko.mail.verification")
data class MailProperties(
    val ttlSeconds: Long = 600L,
    val codeLength: Int = 6,
    val sendIntervalSeconds: Long = 60L,
    val dailyLimit: Long = 10L,
    val from: String = "",
    val subjectPrefix: String = "NekoBackend",
)
