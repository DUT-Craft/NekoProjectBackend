package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException

/** 项目控制密码在 BCrypt 编码前的统一业务校验。 */
internal object ProjectControlPasswordPolicy {
    private const val MIN_LENGTH = 6
    private const val MAX_BCRYPT_PASSWORD_BYTES = 72

    fun normalizeOptional(value: String?): String? {
        val normalized = value?.trim()
        if (normalized.isNullOrBlank()) {
            return null
        }
        validate(normalized)
        return normalized
    }

    fun normalizeRequired(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw ParamErrorException("新控制密码不能为空")
        }
        validate(normalized)
        return normalized
    }

    private fun validate(value: String) {
        if (value.length < MIN_LENGTH) {
            throw ParamErrorException("项目控制密码至少需要 $MIN_LENGTH 位")
        }
        if (value.toByteArray(Charsets.UTF_8).size > MAX_BCRYPT_PASSWORD_BYTES) {
            throw ParamErrorException("项目控制密码的 UTF-8 长度不能超过 $MAX_BCRYPT_PASSWORD_BYTES 字节")
        }
    }
}
