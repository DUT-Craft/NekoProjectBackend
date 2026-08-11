package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import java.net.URI

/** Normalizes image addresses while rejecting browser-executable or ambiguous URL forms. */
object ImageUrlPolicy {
    fun normalize(value: String?, maxLength: Int, fieldName: String): String? {
        val normalized = value?.trim()?.ifBlank { null } ?: return null
        if (normalized.length > maxLength) {
            throw ParamErrorException("$fieldName 不能超过 $maxLength 个字符")
        }
        if (normalized.any { it.isISOControl() } || '\\' in normalized) {
            throw ParamErrorException("$fieldName 格式不正确")
        }
        if (normalized.startsWith("//")) {
            throw ParamErrorException("$fieldName 不能使用省略协议的地址")
        }

        // A single leading slash is a same-origin asset path. This keeps uploaded
        // /api/files/... URLs and existing site assets deployable behind a proxy.
        if (normalized.startsWith('/')) {
            return normalized
        }

        val uri = try {
            URI(normalized)
        } catch (_: Exception) {
            throw ParamErrorException("$fieldName 格式不正确")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES || uri.host.isNullOrBlank() || uri.userInfo != null) {
            throw ParamErrorException("$fieldName 仅支持本站路径或 HTTP(S) 地址")
        }
        return normalized
    }

    private val ALLOWED_SCHEMES = setOf("http", "https")
}
