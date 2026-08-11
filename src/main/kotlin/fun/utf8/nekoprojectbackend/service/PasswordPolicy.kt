package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException

/** Password rules shared by account creation, password change and reset. */
object PasswordPolicy {
    fun validate(password: String, username: String? = null, email: String? = null) {
        if (password.length < MIN_LENGTH) {
            throw ParamErrorException("密码至少需要 $MIN_LENGTH 位")
        }
        if (password.toByteArray(Charsets.UTF_8).size > MAX_BCRYPT_BYTES) {
            throw ParamErrorException("密码的 UTF-8 长度不能超过 $MAX_BCRYPT_BYTES 字节")
        }

        val classes = listOf(LOWER, UPPER, DIGIT, SPECIAL).count { it.containsMatchIn(password) }
        if (classes < MIN_CHARACTER_CLASSES) {
            throw ParamErrorException("密码需包含大写字母、小写字母、数字、特殊字符中的至少三类")
        }

        val lowered = password.lowercase()
        if (lowered in WEAK_PASSWORDS) {
            throw ParamErrorException("密码过于常见，请更换")
        }
        username?.trim()?.takeIf { it.length >= 3 }?.let {
            if (password.contains(it, ignoreCase = true)) {
                throw ParamErrorException("密码不能包含用户名")
            }
        }
        email?.substringBefore('@')?.trim()?.takeIf { it.length >= 3 }?.let {
            if (password.contains(it, ignoreCase = true)) {
                throw ParamErrorException("密码不能包含邮箱前缀")
            }
        }
    }

    private const val MIN_LENGTH = 8
    private const val MAX_BCRYPT_BYTES = 72
    private const val MIN_CHARACTER_CLASSES = 3
    private val LOWER = Regex("[a-z]")
    private val UPPER = Regex("[A-Z]")
    private val DIGIT = Regex("\\d")
    private val SPECIAL = Regex("[^A-Za-z0-9]")
    private val WEAK_PASSWORDS = setOf(
        "12345678",
        "123456789",
        "password",
        "password1",
        "qwerty123",
        "abc12345",
        "admin123",
        "11111111",
        "00000000",
        "nekobox123",
    )
}
