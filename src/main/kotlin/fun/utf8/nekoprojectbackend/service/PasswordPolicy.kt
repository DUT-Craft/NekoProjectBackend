package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException

/**
 * 密码强度策略（设计 §6.1）：
 * - 长度 8–64；
 * - 至少包含大写 / 小写 / 数字 / 特殊字符中的三类；
 * - 禁止常见弱密码；
 * - 禁止包含用户名或邮箱前缀的明显变体。
 *
 * 集中校验，供注册 / 改密 / 重置复用。后端是最终安全边界，前端校验仅为体验。
 */
object PasswordPolicy {
    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 64

    private val LOWER = Regex("[a-z]")
    private val UPPER = Regex("[A-Z]")
    private val DIGIT = Regex("\\d")
    private val SPECIAL = Regex("[^A-Za-z0-9]")

    /** 常见弱密码黑名单（小写比对）。 */
    private val WEAK = setOf(
        "12345678", "123456789", "1234567890", "password", "password1",
        "qwerty12", "qwerty123", "abc12345", "iloveyou", "letmein1",
        "admin123", "welcome1", "monkey12", "dragon12", "11111111",
        "00000000", "aaaa1111", "nekobox123",
    )

    /**
     * 校验密码强度，不合规抛 [ParamErrorException]。
     *
     * @param username 用户名（密码不能包含，可空）
     * @param emailPrefix 邮箱本地部分（密码不能包含，可空）
     */
    fun validate(password: String, username: String? = null, emailPrefix: String? = null) {
        if (password.length !in MIN_LENGTH..MAX_LENGTH) {
            throw ParamErrorException("密码长度需为 $MIN_LENGTH-$MAX_LENGTH 位")
        }
        val classes = listOf(LOWER, UPPER, DIGIT, SPECIAL).count { it.containsMatchIn(password) }
        if (classes < 3) {
            throw ParamErrorException("密码需包含大写、小写、数字、特殊字符中的至少三类")
        }
        if (password.lowercase() in WEAK) {
            throw ParamErrorException("密码过于常见，请更换")
        }
        if (username != null && username.isNotBlank()) {
            requireNotContained(password, username, "密码不能包含用户名")
        }
        if (emailPrefix != null && emailPrefix.isNotBlank()) {
            requireNotContained(password, emailPrefix, "密码不能包含邮箱前缀")
        }
    }

    private fun requireNotContained(password: String, fragment: String, message: String) {
        if (fragment.length >= 3 && password.contains(fragment, ignoreCase = true)) {
            throw ParamErrorException(message)
        }
    }
}
