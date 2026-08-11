package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException

/**
 * 用户名策略（设计 §3.1）：
 * - 长度 3–32；
 * - 字符集仅允许字母、数字、下划线、短横线；
 * - 禁止邮箱格式（避免与邮箱登录标识歧义）；
 * - 禁止系统保留名（admin / system 等，大小写不敏感）；
 * - 唯一性校验不区分大小写（见 [User.usernameLower]，由调用方查重）。
 *
 * 后端是最终安全边界，前端校验仅为体验。
 */
object UsernamePolicy {
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 32

    private val ALLOWED = Regex("^[A-Za-z0-9_-]+$")
    private val EMAIL_LIKE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** 系统保留名，禁止注册（大小写不敏感）。 */
    private val RESERVED = setOf(
        "admin", "administrator", "system", "official", "root", "support",
        "superuser", "moderator", "mod", "staff", "help", "info",
    )

    /** 校验并归一化用户名：返回 trim 后的值，不合规抛 [ParamErrorException]。 */
    fun validate(raw: String): String {
        val username = raw.trim()
        if (username.length !in MIN_LENGTH..MAX_LENGTH) {
            throw ParamErrorException("用户名长度需为 $MIN_LENGTH-$MAX_LENGTH 个字符")
        }
        if (!ALLOWED.matches(username)) {
            throw ParamErrorException("用户名只能包含字母、数字、下划线和短横线")
        }
        if (EMAIL_LIKE.matches(username)) {
            throw ParamErrorException("用户名不能使用邮箱格式")
        }
        if (username.lowercase() in RESERVED) {
            throw ParamErrorException("该用户名为系统保留名，请更换")
        }
        return username
    }

    /** 归一化为大小写不敏感比较键（与 [User.usernameLower] 一致）。 */
    fun normalizeKey(raw: String): String = raw.trim().lowercase()
}
