package top.foxball.nekomainsite.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import top.foxball.nekomainsite.config.FileProperties
import top.foxball.nekomainsite.entity.jdbc.AuditLog
import top.foxball.nekomainsite.entity.jdbc.MediaAsset
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.ContentDraftRepository
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.shared.requirePresent
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

data class MediaDeletionView(val id: Long, val deleted: Boolean)
data class PublicMediaResource(val mimeType: String, val bytes: ByteArray)

@Service
@Transactional(readOnly = true)
class MediaService(
    private val fileProperties: FileProperties,
    private val draftRepository: ContentDraftRepository,
    private val mediaRepository: MediaAssetRepository,
    private val auditLogRepository: AuditLogRepository,
    private val contentManagementService: ContentManagementService,
) {
    @Transactional
    fun uploadDraftImage(
        draftId: String,
        purpose: String,
        altText: String?,
        caption: String?,
        file: MultipartFile,
        operatorId: Long?,
    ): AdminMediaView {
        val draft = draftRepository.findById(draftId).orElseThrow { ResourceNotFoundException("草稿不存在") }
        if (mediaRepository.countByDraftIdAndDeletedAtIsNull(draftId) >= MAX_IMAGES_PER_DRAFT) {
            throw ParamErrorException("单份草稿最多上传 $MAX_IMAGES_PER_DRAFT 张图片")
        }
        val uploaded = storeImage(draftId, draft.resourceType, purpose, altText, caption, file, operatorId)
        audit(operatorId, draft.resourceType, uploaded.id.toString(), "MEDIA_UPLOAD")
        return mediaView(uploaded)
    }

    @Transactional
    fun updateImage(id: Long, altText: String?, caption: String?, operatorId: Long?): AdminMediaView {
        val item = mediaRepository.findById(id).orElseThrow { ResourceNotFoundException("图片不存在") }
        if (item.deletedAt != null) throw ResourceNotFoundException("图片不存在")
        item.altText = altText?.trim()?.take(255)
        item.caption = caption?.trim()?.take(500)
        val saved = mediaRepository.save(item)
        audit(operatorId, item.resourceType, id.toString(), "MEDIA_UPDATE")
        return mediaView(saved)
    }

    @Transactional
    fun deleteImage(id: Long, operatorId: Long?): MediaDeletionView {
        val item = mediaRepository.findById(id).orElseThrow { ResourceNotFoundException("图片不存在") }
        if (item.deletedAt != null) return MediaDeletionView(id, true)
        if (contentManagementService.isMediaReferenced(id)) throw ConflictException("图片仍被内容引用，请先从草稿或正文中移除")
        item.deletedAt = Instant.now()
        mediaRepository.save(item)
        audit(operatorId, item.resourceType, id.toString(), "MEDIA_DELETE")
        return MediaDeletionView(id, true)
    }

    fun readImage(filename: String, allowUnpublished: Boolean): PublicMediaResource? {
        if (!STORAGE_NAME_REGEX.matches(filename)) return null
        val item = mediaRepository.findByStorageNameAndDeletedAtIsNull(filename) ?: return null
        if (!allowUnpublished && !contentManagementService.isMediaPubliclyReadable(item)) return null
        val root = storageRoot()
        val target = root.resolve(item.storageName).normalize()
        if (target.parent != root || !Files.isRegularFile(target)) return null
        return PublicMediaResource(item.mimeType, Files.readAllBytes(target))
    }

    private fun storeImage(
        draftId: String,
        resourceType: String,
        purpose: String,
        altText: String?,
        caption: String?,
        file: MultipartFile,
        operatorId: Long?,
    ): MediaAsset {
        if (file.isEmpty) throw ParamErrorException("图片不能为空")
        if (file.size > fileProperties.image.maxSizeMb * 1024 * 1024) throw ParamErrorException("图片超过大小限制")
        val normalizedPurpose = purpose.trim().uppercase()
        if (normalizedPurpose !in ALLOWED_PURPOSES) throw ParamErrorException("图片用途不支持")
        val extension = file.originalFilename.orEmpty().substringAfterLast('.', "").lowercase()
        if (extension !in fileProperties.image.allowedExtensions.map(String::lowercase)) throw ParamErrorException("图片格式不支持")
        val bytes = file.bytes
        if (!matchesImageSignature(extension, bytes)) throw ParamErrorException("文件内容不是有效图片")
        val canonicalMime = mimeType(extension)
        val suppliedMime = file.contentType?.lowercase()?.substringBefore(';')
        if (suppliedMime == null || suppliedMime !in acceptedMimeTypes(extension)) {
            throw ParamErrorException("图片 MIME 类型与扩展名不一致")
        }
        val dimensions = imageDimensions(bytes) ?: throw ParamErrorException("图片损坏或无法解析")
        if (dimensions.width !in 1..MAX_IMAGE_DIMENSION || dimensions.height !in 1..MAX_IMAGE_DIMENSION ||
            dimensions.width.toLong() * dimensions.height > MAX_IMAGE_PIXELS
        ) {
            throw ParamErrorException("图片尺寸或总像素超过限制")
        }
        if (!decodesCompletely(bytes, dimensions)) throw ParamErrorException("图片损坏或无法完整解码")
        validateAspectRatio(normalizedPurpose, dimensions)

        val filename = "${UUID.randomUUID()}.$extension"
        val root = storageRoot()
        Files.createDirectories(root)
        val target = root.resolve(filename)
        Files.write(target, bytes)
        val asset = MediaAsset(
            draftId = draftId,
            resourceType = resourceType,
            resourceId = null,
            purpose = normalizedPurpose,
            originalName = file.originalFilename.orEmpty().take(255).ifBlank { "image.$extension" },
            storageName = filename,
            mimeType = canonicalMime,
            sizeBytes = bytes.size.toLong(),
            width = dimensions.width,
            height = dimensions.height,
            altText = altText?.trim()?.take(255),
            caption = caption?.trim()?.take(500),
            createdBy = operatorId,
        )
        return try {
            mediaRepository.save(asset)
        } catch (error: Exception) {
            try {
                Files.deleteIfExists(target)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    private fun mediaView(item: MediaAsset) = AdminMediaView(
        id = item.id.requirePresent("MediaAsset.id"),
        url = "${fileProperties.baseUrl.trimEnd('/')}/api/public/media/${item.storageName}",
        purpose = item.purpose,
        originalName = item.originalName,
        mimeType = item.mimeType,
        sizeBytes = item.sizeBytes,
        width = item.width,
        height = item.height,
        altText = item.altText,
        caption = item.caption,
    )

    private fun storageRoot(): Path = Path.of(fileProperties.storagePath).toAbsolutePath().normalize()

    private fun mimeType(extension: String) = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun imageDimensions(bytes: ByteArray): ImageDimensions? = runCatching {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return@runCatching null
        stream.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = input
                ImageDimensions(reader.getWidth(0), reader.getHeight(0))
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private fun decodesCompletely(bytes: ByteArray, expected: ImageDimensions): Boolean = runCatching {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return@runCatching false
        stream.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use false
            val reader = readers.next()
            try {
                reader.input = input
                if (reader.getWidth(0) != expected.width || reader.getHeight(0) != expected.height) return@use false
                val image = reader.read(0)
                image.width == expected.width && image.height == expected.height
            } finally {
                reader.dispose()
            }
        }
    }.getOrDefault(false)

    private fun validateAspectRatio(purpose: String, dimensions: ImageDimensions) {
        if (purpose == "ICON" && dimensions.width != dimensions.height) {
            throw ParamErrorException("自定义图标必须是 1:1 正方形")
        }
        if (purpose == "COVER") {
            val ratio = dimensions.width.toDouble() / dimensions.height
            if (kotlin.math.abs(ratio - 16.0 / 9.0) > 0.01) throw ParamErrorException("封面必须裁剪为 16:9")
        }
    }

    private fun acceptedMimeTypes(extension: String): Set<String> = when (extension) {
        "jpg", "jpeg" -> setOf("image/jpeg", "image/jpg")
        "png" -> setOf("image/png")
        else -> setOf("image/webp")
    }

    private fun audit(operatorId: Long?, resourceType: String, resourceId: String, action: String) {
        operatorId?.let {
            auditLogRepository.save(AuditLog(operatorUserId = it, resourceType = resourceType, resourceId = resourceId, action = action))
        }
    }

    private fun matchesImageSignature(extension: String, bytes: ByteArray): Boolean = when (extension) {
        "jpg", "jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        )
        "webp" -> bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" &&
            riffPayloadSize(bytes) == bytes.size.toLong() - 8
        else -> false
    }

    private fun riffPayloadSize(bytes: ByteArray): Long =
        (bytes[4].toLong() and 0xFF) or
            ((bytes[5].toLong() and 0xFF) shl 8) or
            ((bytes[6].toLong() and 0xFF) shl 16) or
            ((bytes[7].toLong() and 0xFF) shl 24)

    private data class ImageDimensions(val width: Int, val height: Int)

    private companion object {
        const val MAX_IMAGE_DIMENSION = 4096
        const val MAX_IMAGE_PIXELS = 12_000_000L
        const val MAX_IMAGES_PER_DRAFT = 100L
        val ALLOWED_PURPOSES = setOf("COVER", "ICON", "CONTENT", "GALLERY")
        val STORAGE_NAME_REGEX = Regex("[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)")
    }
}
