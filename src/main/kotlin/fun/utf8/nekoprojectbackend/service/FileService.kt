package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.FileProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileRecord
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileRecordRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.handlder.UnauthorizedException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.time.LocalDateTime
import java.util.Locale
import javax.imageio.ImageIO

data class FileUploadResponse(
    val id: Long?,
    val storedName: String,
    val originalName: String,
    val mimeType: String?,
    val size: Long?,
    val category: FileCategory,
    val url: String,
    val createTime: LocalDateTime?,
)

data class FilePageVO(
    val content: List<FileUploadResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

/** 文件上传/下载业务：校验、落盘、元数据落库、读取鉴权。 */
@Service
class FileService(
    private val storageService: StorageService,
    private val fileRecordRepository: FileRecordRepository,
    private val objectItemRepository: ObjectItemRepository,
    private val properties: FileProperties,
) {

    @Transactional
    fun upload(
        file: MultipartFile,
        category: FileCategory,
        user: LoginUser?,
        objectItemId: Int? = null,
    ): FileUploadResponse {
        if (file.isEmpty) throw ParamErrorException("文件为空")

        val originalName = normalizeOriginalName(file.originalFilename)
        val extension = extractExtension(originalName)
        val policy = policyOf(category)

        objectItemId?.let { id ->
            if (id <= 0) {
                throw ParamErrorException("项目条目 ID 必须大于 0")
            }
            if (!objectItemRepository.existsById(id)) {
                throw ResourceNotFoundException("项目条目不存在")
            }
        }

        validateExtension(extension, policy, category)
        validateSize(file.size, policy, category)
        validateContent(file, category, extension)

        val storedName = storageService.store(file, extension)
        try {
            val record = FileRecord().apply {
                this.storedName = storedName
                this.originalName = originalName
                this.mimeType = file.contentType
                this.size = file.size
                this.category = category
                this.extension = extension
                this.uploaderId = user?.id
                this.objectItemId = objectItemId
                // 图片默认可公开读，文档默认私有
                this.publicRead = category == FileCategory.IMAGE && properties.publicReadImage
                this.createTime = LocalDateTime.now()
            }
            val saved = fileRecordRepository.save(record)
            return saved.toUploadResponse()
        } catch (ex: Exception) {
            // 数据库写入失败时回收已落盘文件，避免磁盘与元数据逐渐分叉。
            storageService.delete(storedName)
            throw ex
        }
    }

    /** 列出当前用户上传的文件（按上传时间倒序分页）。 */
    @Transactional(readOnly = true)
    fun listByUploader(user: LoginUser, page: Int, size: Int): FilePageVO {
        val safePage = if (page < 0) 0 else page
        val safeSize = if (size <= 0) DEFAULT_LIST_SIZE else minOf(size, MAX_LIST_SIZE)
        val pageable = PageRequest.of(safePage, safeSize)
        val result = fileRecordRepository.findByUploaderIdOrderByCreateTimeDesc(user.id, pageable)
        return FilePageVO(
            content = result.content.map { it.toUploadResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size,
        )
    }

    /** 下载：返回记录与输入流（流由控制器关闭）。鉴权在此判定。 */
    fun loadForDownload(storedName: String, user: LoginUser?): Pair<FileRecord, InputStream> {
        val record = fileRecordRepository.findByStoredName(storedName)
            ?: throw ResourceNotFoundException("文件不存在")
        val publicOk = record.publicRead == true &&
                record.category == FileCategory.IMAGE &&
                properties.publicReadImage
        if (!publicOk) {
            // 项目文件跟随当前项目归属；未绑定项目的文件才跟随最初上传者。
            if (user == null) {
                throw UnauthorizedException("请登录后下载")
            }
            if (!canManage(record, user)) {
                throw ForbiddenException("无权下载该文件")
            }
        }
        val stream = storageService.openInputStream(storedName)
        return record to stream
    }

    @Transactional
    fun delete(storedName: String, user: LoginUser) {
        val record = fileRecordRepository.findByStoredName(storedName)
            ?: throw ResourceNotFoundException("文件不存在")
        if (!canManage(record, user)) {
            throw ForbiddenException("无权删除该文件")
        }
        fileRecordRepository.delete(record)
        storageService.delete(storedName)
    }

    private fun canManage(record: FileRecord, user: LoginUser): Boolean {
        if (user.role == Role.SUPER_ADMIN) return true
        val objectItemId = record.objectItemId
        if (objectItemId == null) {
            return record.uploaderId == user.id
        }
        return objectItemRepository.findById(objectItemId)
            .map { it.ownerId == user.id }
            .orElse(false)
    }

    private fun policyOf(category: FileCategory): FileProperties.TypePolicy =
        if (category == FileCategory.IMAGE) properties.image else properties.document

    private fun validateExtension(ext: String, policy: FileProperties.TypePolicy, category: FileCategory) {
        if (category == FileCategory.IMAGE && ext in BLOCKED_IMAGE_EXTENSIONS) {
            throw ParamErrorException("出于安全原因，不支持上传 SVG 图片，请转换为 PNG 或 WebP")
        }
        if (policy.allowedExtensions.isNotEmpty() && ext !in policy.allowedExtensions) {
            throw ParamErrorException("不支持的${category}扩展名：$ext")
        }
    }

    private fun validateSize(size: Long, policy: FileProperties.TypePolicy, category: FileCategory) {
        val maxBytes = policy.maxSizeMb * 1024 * 1024
        if (size > maxBytes) {
            throw ParamErrorException("${category}大小超过 ${policy.maxSizeMb}MB 限制")
        }
    }

    /**
     * 内容真实性校验：防伪装扩展名（如 .exe 改名 .png）。
     * - 图片扩展名、MIME 和文件签名必须互相匹配；
     * - JDK 原生支持的格式再用 ImageIO 完整试读，排除损坏文件。
     * 仅扩展名校验不够——客户端可任意伪造文件名与 Content-Type。
     */
    private fun validateContent(file: MultipartFile, category: FileCategory, extension: String) {
        if (category != FileCategory.IMAGE) return

        val mime = file.contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: throw ParamErrorException("图片文件 MIME 不能为空")
        val expectedMimes = IMAGE_MIMES_BY_EXTENSION[extension]
            ?: throw ParamErrorException("无法安全校验该图片格式：$extension")
        if (mime !in expectedMimes) {
            throw ParamErrorException("图片扩展名 .$extension 与 MIME $mime 不匹配")
        }

        val header = file.inputStream.use { it.readNBytes(IMAGE_SIGNATURE_BYTES) }
        if (!matchesImageSignature(extension, header)) {
            throw ParamErrorException("图片扩展名、MIME 与文件内容不匹配")
        }

        if (extension in IMAGEIO_DECODABLE_EXTS) {
            val decoded = try {
                file.inputStream.use { ImageIO.read(it) }
            } catch (_: Exception) {
                null
            }
            if (decoded == null) {
                throw ParamErrorException("文件不是有效的图片（内容无法解析），请确认文件完整性")
            }
        }
    }

    private fun matchesImageSignature(extension: String, header: ByteArray): Boolean {
        fun byteAt(index: Int): Int = header[index].toInt() and 0xff
        fun startsWith(vararg expected: Int): Boolean =
            header.size >= expected.size && expected.indices.all { byteAt(it) == expected[it] }

        return when (extension) {
            "jpg", "jpeg" -> startsWith(0xff, 0xd8, 0xff)
            "png" -> startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            "gif" -> startsWith(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) ||
                    startsWith(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
            "bmp" -> startsWith(0x42, 0x4d)
            "webp" -> header.size >= 12 &&
                    startsWith(0x52, 0x49, 0x46, 0x46) &&
                    byteAt(8) == 0x57 && byteAt(9) == 0x45 && byteAt(10) == 0x42 && byteAt(11) == 0x50
            else -> false
        }
    }

    private fun extractExtension(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx >= 0) name.substring(idx + 1).lowercase() else ""
    }

    /** 只保留展示用基名，并拒绝控制字符和超长元数据。 */
    private fun normalizeOriginalName(name: String?): String {
        val normalized = name?.trim().orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "unnamed" }
        if (normalized.any { it.code == 0 || it.code < 0x20 || it.code == 0x7f }) {
            throw ParamErrorException("文件名包含非法控制字符")
        }
        if (normalized.length > MAX_ORIGINAL_NAME_LENGTH) {
            throw ParamErrorException("文件名不能超过 $MAX_ORIGINAL_NAME_LENGTH 个字符")
        }
        return normalized
    }

    private fun buildUrl(storedName: String, category: FileCategory?): String {
        val base = "${properties.baseUrl.trimEnd('/')}/api/files/$storedName"
        return if (category == FileCategory.IMAGE) "$base?inline=true" else base
    }

    private fun FileRecord.toUploadResponse(): FileUploadResponse = FileUploadResponse(
        id = id,
        storedName = storedName ?: "",
        originalName = originalName ?: "",
        mimeType = mimeType,
        size = size,
        category = category ?: FileCategory.DOCUMENT,
        url = buildUrl(storedName ?: "", category),
        createTime = createTime,
    )

    private companion object {
        const val DEFAULT_LIST_SIZE = 20
        const val MAX_LIST_SIZE = 100
        const val MAX_ORIGINAL_NAME_LENGTH = 255

        const val IMAGE_SIGNATURE_BYTES = 12
        val BLOCKED_IMAGE_EXTENSIONS = setOf("svg")

        val IMAGE_MIMES_BY_EXTENSION = mapOf(
            "jpg" to setOf("image/jpeg"),
            "jpeg" to setOf("image/jpeg"),
            "png" to setOf("image/png"),
            "gif" to setOf("image/gif"),
            "webp" to setOf("image/webp"),
            "bmp" to setOf("image/bmp", "image/x-bmp", "image/x-ms-bmp"),
        )

        /** JDK ImageIO 原生可解码的图片格式；WebP 由签名校验保护。 */
        val IMAGEIO_DECODABLE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp")
    }
}
