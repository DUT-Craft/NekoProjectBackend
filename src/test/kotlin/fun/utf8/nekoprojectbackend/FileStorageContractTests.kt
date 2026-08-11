package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.FileProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileRecordRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.service.FileService
import `fun`.utf8.nekoprojectbackend.service.StorageService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FileStorageContractTests {

    @Test
    fun `upload removes the stored file when metadata persistence fails`() {
        val root = Files.createTempDirectory("neko-file-upload-test")
        try {
            val properties = FileProperties(
                storagePath = root.toString(),
                image = FileProperties.TypePolicy(allowedExtensions = listOf("png"), maxSizeMb = 1),
            )
            val storage = StorageService(properties)
            val service = FileService(
                storageService = storage,
                fileRecordRepository = throwingRepository(FileRecordRepository::class.java),
                objectItemRepository = throwingRepository(ObjectItemRepository::class.java),
                properties = properties,
            )

            assertFailsWith<IllegalStateException> {
                service.upload(
                    MockMultipartFile(
                        "file",
                        "cover.png",
                        "image/png",
                        pngBytes(),
                    ),
                    `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory.IMAGE,
                    user = null,
                )
            }

            Files.walk(root).use { paths ->
                assertFalse(paths.anyMatch { Files.isRegularFile(it) })
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `svg upload is rejected even when configuration includes svg`() {
        val root = Files.createTempDirectory("neko-svg-upload-test")
        try {
            val service = fileService(root, listOf("svg"))

            assertFailsWith<ParamErrorException> {
                service.upload(
                    MockMultipartFile(
                        "file",
                        "cover.svg",
                        "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray(),
                    ),
                    `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory.IMAGE,
                    user = null,
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `image extension mime and signature must agree`() {
        val root = Files.createTempDirectory("neko-image-signature-test")
        try {
            val service = fileService(root, listOf("jpg"))

            assertFailsWith<ParamErrorException> {
                service.upload(
                    MockMultipartFile("file", "cover.jpg", "image/jpeg", pngBytes()),
                    `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory.IMAGE,
                    user = null,
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `webp upload requires riff webp signature`() {
        val root = Files.createTempDirectory("neko-webp-signature-test")
        try {
            val service = fileService(root, listOf("webp"))

            assertFailsWith<ParamErrorException> {
                service.upload(
                    MockMultipartFile("file", "cover.webp", "image/webp", "not-webp".toByteArray()),
                    `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory.IMAGE,
                    user = null,
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun fileService(root: java.nio.file.Path, allowedExtensions: List<String>): FileService {
        val properties = FileProperties(
            storagePath = root.toString(),
            image = FileProperties.TypePolicy(allowedExtensions = allowedExtensions, maxSizeMb = 1),
        )
        return FileService(
            storageService = StorageService(properties),
            fileRecordRepository = throwingRepository(FileRecordRepository::class.java),
            objectItemRepository = throwingRepository(ObjectItemRepository::class.java),
            properties = properties,
        )
    }

    private fun pngBytes(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> throwingRepository(type: Class<T>): T {
        val handler = InvocationHandler { _, _, _ ->
            throw IllegalStateException("database unavailable")
        }
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T
    }
}
