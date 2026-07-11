package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.FileProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileRecord
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileRecordRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.time.LocalDateTime
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

        val originalName = file.originalFilename ?: "unnamed"
        val extension = extractExtension(originalName)
        val policy = policyOf(category)

        validateExtension(extension, policy, category)
        validateSize(file.size, policy, category)
        validateContent(file, category)

        val storedName = storageService.store(file, extension)
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
            // 私有文件：须登录且为所有者或总管理，防止已登录的任意用户越权下载他人文档
            if (user == null) {
                throw ForbiddenException("请登录后下载")
            }
            val isOwner = record.uploaderId == user.id
            val isSuper = user.role == Role.SUPER_ADMIN
            if (!isOwner && !isSuper) {
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
        val isOwner = record.uploaderId == user.id
        val isSuper = user.role == Role.SUPER_ADMIN
        if (!isOwner && !isSuper) {
            throw ForbiddenException("无权删除该文件")
        }
        fileRecordRepository.delete(record)
        storageService.delete(storedName)
    }

    private fun policyOf(category: FileCategory): FileProperties.TypePolicy =
        if (category == FileCategory.IMAGE) properties.image else properties.document

    private fun validateExtension(ext: String, policy: FileProperties.TypePolicy, category: FileCategory) {
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
     * - 图片：用 ImageIO 试读，读不出（返回 null）即判定非真实图片；
     * - MIME 一致性：file.contentType 若提供，需与声明的 category 大类匹配（图片 MIME 需以 image 开头）。
     * 仅扩展名校验不够——客户端可任意伪造文件名与 Content-Type。
     */
    private fun validateContent(file: MultipartFile, category: FileCategory) {
        val mime = file.contentType
        if (!mime.isNullOrBlank()) {
            val isImageMime = mime.startsWith("image/", ignoreCase = true)
            if (category == FileCategory.IMAGE && !isImageMime) {
                throw ParamErrorException("图片文件的 MIME 不是 image 类型：$mime")
            }
        }
        if (category == FileCategory.IMAGE) {
            val ext = extractExtension(file.originalFilename ?: "")
            // ImageIO 只内置支持 jpg/png/gif/bmp；svg（XML）和 webp（需第三方插件）
            // 不在支持范围内，对这两种格式跳过 ImageIO 试读，仅依赖 MIME 校验。
            if (ext in IMAGEIO_DECODABLE_EXTS) {
                val decoded = try {
                    file.inputStream.use { ImageIO.read(it) }
                } catch (e: Exception) {
                    null
                }
                if (decoded == null) {
                    throw ParamErrorException("文件不是有效的图片（内容无法解析），请确认文件完整性")
                }
            }
        }
    }

    private fun extractExtension(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx >= 0) name.substring(idx + 1).lowercase() else ""
    }

    private fun buildUrl(storedName: String): String =
        "${properties.baseUrl.trimEnd('/')}/api/files/$storedName"

    private fun FileRecord.toUploadResponse(): FileUploadResponse = FileUploadResponse(
        id = id,
        storedName = storedName ?: "",
        originalName = originalName ?: "",
        mimeType = mimeType,
        size = size,
        category = category ?: FileCategory.DOCUMENT,
        url = buildUrl(storedName ?: ""),
        createTime = createTime,
    )

    private companion object {
        const val DEFAULT_LIST_SIZE = 20
        const val MAX_LIST_SIZE = 100

        /** JDK ImageIO 原生可解码的图片格式；svg/webp 需第三方插件，跳过试读。 */
        val IMAGEIO_DECODABLE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp")
    }
}
