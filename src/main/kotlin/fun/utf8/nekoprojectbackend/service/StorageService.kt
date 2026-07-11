package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.FileProperties
import `fun`.utf8.nekoprojectbackend.handlder.BusinessException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 本地磁盘存储：落盘、按存储名读取。职责单一，不碰业务校验，
 * 便于日后替换为 OSS/S3/MinIO 实现（抽成接口后本类改为 LocalFileStorage）。
 */
@Service
class StorageService(
    private val properties: FileProperties,
) {
    private val root: Path = Paths.get(properties.storagePath).toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    /** 落盘，返回相对存储名（含日期子目录），如 2026/07/11/<uuid>.png。 */
    fun store(file: MultipartFile, extension: String): String {
        val dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val fileName = "${UUID.randomUUID()}.$extension"
        val relative = "$dateDir/$fileName"
        val target = resolveAndGuard(relative)

        Files.createDirectories(target.parent)
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return relative
    }

    /** 读取为流。调用方负责关闭。 */
    fun openInputStream(storedName: String): InputStream {
        val path = resolveAndGuard(storedName)
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw ResourceNotFoundException("文件不存在或已被删除")
        }
        return Files.newInputStream(path)
    }

    fun exists(storedName: String): Boolean =
        runCatching { Files.isRegularFile(resolveAndGuard(storedName)) }.getOrDefault(false)

    fun delete(storedName: String) {
        runCatching { Files.deleteIfExists(resolveAndGuard(storedName)) }
    }

    /** 解析存储名为绝对路径，并强制最终路径仍在 root 之内，防目录穿越。 */
    private fun resolveAndGuard(storedName: String): Path {
        val path = root.resolve(storedName).normalize()
        if (!path.startsWith(root)) {
            throw BusinessException(HttpStatus.BAD_REQUEST, "非法的文件路径")
        }
        return path
    }
}
