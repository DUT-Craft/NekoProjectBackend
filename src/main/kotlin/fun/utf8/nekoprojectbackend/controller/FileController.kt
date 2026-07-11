package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.FileService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/** 文件接口（/api/files）：上传、下载/预览、删除。 */
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService,
    private val builder: ResponseBuilder,
) {
    /** 上传：multipart/form-data，字段名 file，type 取 IMAGE|DOCUMENT。 */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam type: FileCategory,
        @RequestParam(required = false) objectItemId: Int?,
        @AuthenticationPrincipal user: LoginUser?,
    ): ResponseEntity<Response> {
        val result = fileService.upload(file, type, user, objectItemId)
        return builder.ok().data(result).build()
    }

    /** 我的文件列表（当前登录用户上传，分页）。需 JWT。 */
    @GetMapping("/mine")
    fun listMine(
        @AuthenticationPrincipal user: LoginUser,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ResponseEntity<Response> {
        val result = fileService.listByUploader(user, page, size)
        return builder.ok().data(result).build()
    }

    /** 下载/预览：inline=true 预览(图片)，默认下载(attachment)。 */
    @GetMapping("/{storedName:.+}")
    fun download(
        @PathVariable storedName: String,
        @RequestParam(required = false, defaultValue = "false") inline: Boolean,
        @AuthenticationPrincipal user: LoginUser?,
    ): ResponseEntity<InputStreamResource> {
        val (record, stream) = fileService.loadForDownload(storedName, user)
        // SVG 可内嵌 <script>：浏览器以 image/svg+xml 内联渲染时会执行脚本，构成同源存储型 XSS
        // （可窃取 JS 可读的 access token）。对 SVG 强制下载，并加 CSP 纵深防御；
        // <img> 标签加载封面图不受 Content-Disposition 影响，仍可正常显示。
        val isSvg = record.mimeType?.equals("image/svg+xml", ignoreCase = true) == true ||
                record.extension?.equals("svg", ignoreCase = true) == true
        val disposition = if (inline && !isSvg) "inline" else "attachment"
        val contentType = MediaType.parseMediaType(record.mimeType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE)
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(disposition, record.originalName))
            .contentLength(record.size ?: -1)
            .header("X-Content-Type-Options", "nosniff")
            .apply {
                if (isSvg) {
                    // 即便被内联渲染，CSP 阻止脚本执行与外部资源引用
                    header("Content-Security-Policy", "default-src 'none'")
                }
            }
            .body(InputStreamResource(stream))
    }

    /**
     * 构建防注入的 Content-Disposition 头：
     * - 清洗 originalName 中的控制字符、CR/LF、引号（防响应头注入）；
     * - ASCII 文件名放 `filename="..."`，非 ASCII 额外用 RFC 5987 的 `filename*=UTF-8''<percent-encoded>`
     *   避免中文文件名在部分客户端乱码或被拒。
     */
    private fun buildContentDisposition(disposition: String, originalName: String?): String {
        val safeName = sanitizeFileName(originalName)
        val asciiName = sanitizeFileName(originalName, asciiOnly = true).ifEmpty { "file" }
        val base = "$disposition; filename=\"$asciiName\""
        return if (safeName.any { it.code > 127 }) {
            val encoded = encodeRfc5987(safeName)
            "$base; filename*=UTF-8''$encoded"
        } else {
            base
        }
    }

    /** 清洗文件名：去掉 CR/LF 及其他控制字符、去掉引号；asciiOnly=true 时再剔除非 ASCII。 */
    private fun sanitizeFileName(name: String?, asciiOnly: Boolean = false): String {
        if (name.isNullOrBlank()) {
            return ""
        }
        return buildString {
            for (ch in name.trim()) {
                val code = ch.code
                val isControl = code < 0x20 || code == 0x7f
                if (isControl || ch == '"' || ch == '\\') {
                    continue
                }
                if (asciiOnly && code > 127) {
                    continue
                }
                append(ch)
            }
        }.take(200)
    }

    /** RFC 5987 百分号编码（用于 filename*=UTF-8''...）。 */
    private fun encodeRfc5987(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xff
            // 保持 ASCII 字母数字及部分安全符号不编码，其余百分号编码
            val ch = unsigned.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "-._~".toSet()) {
                sb.append(ch)
            } else {
                sb.append('%').append("%02X".format(unsigned))
            }
        }
        return sb.toString()
    }

    @DeleteMapping("/{storedName:.+}")
    fun delete(
        @PathVariable storedName: String,
        @AuthenticationPrincipal user: LoginUser,
    ): ResponseEntity<Response> {
        fileService.delete(storedName, user)
        return builder.ok().build()
    }
}
