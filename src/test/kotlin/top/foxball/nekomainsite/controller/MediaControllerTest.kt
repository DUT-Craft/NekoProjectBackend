package top.foxball.nekomainsite.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.config.FileProperties
import top.foxball.nekomainsite.entity.jdbc.Announcement
import top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.repository.UserRepository
import top.foxball.nekomainsite.service.ContentManagementService
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

@SpringBootTest
@Transactional
class MediaControllerTest @Autowired constructor(
    private val controller: MediaController,
    private val content: ContentManagementService,
    private val announcements: AnnouncementRepository,
    private val media: MediaAssetRepository,
    private val users: UserRepository,
    private val fileProperties: FileProperties,
) {
    @Test
    fun `media is public only while its content is published`() {
        val draft = content.createDraft("announcements", null, 1L)
        val response = controller.uploadDraftImage(
            draft.id,
            "CONTENT",
            "一像素测试图片",
            null,
            MockMultipartFile("file", "pixel.png", "image/png", VALID_PNG),
            adminAuthentication(),
        )
        assertEquals(200, response.statusCode.value())

        val stored = media.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(draft.id).single()
        val path = Path.of(fileProperties.storagePath).toAbsolutePath().normalize().resolve(stored.storageName)
        try {
            assertEquals(1, stored.width)
            assertEquals(1, stored.height)
            assertEquals(404, controller.readImage(stored.storageName, null).statusCode.value())
            assertNotNull(controller.readImage(stored.storageName, adminAuthentication()).body)

            val announcement = announcements.save(
                Announcement(
                    slug = "media-visibility-test",
                    title = "媒体可见性测试",
                    status = AnnouncementStatus.PUBLISHED,
                ),
            )
            stored.draftId = null
            stored.resourceId = announcement.id ?: error("announcement id missing")
            media.save(stored)
            assertNotNull(controller.readImage(stored.storageName, null).body)

            announcement.status = AnnouncementStatus.HIDDEN
            announcements.save(announcement)
            assertEquals(404, controller.readImage(stored.storageName, null).statusCode.value())
            assertNotNull(controller.readImage(stored.storageName, adminAuthentication()).body)

            controller.deleteImage(stored.id ?: error("media id missing"), adminAuthentication())
            assertEquals(404, controller.readImage(stored.storageName, null).statusCode.value())
            assertEquals(404, controller.readImage(stored.storageName, adminAuthentication()).statusCode.value())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `upload rejects malformed mime mismatch and wrong crop ratio`() {
        val draft = content.createDraft("announcements", null, 1L)
        val authentication = adminAuthentication()

        assertThrows(ParamErrorException::class.java) {
            controller.uploadDraftImage(
                draft.id, "CONTENT", "损坏图片", null,
                MockMultipartFile("file", "broken.png", "image/png", PNG_SIGNATURE), authentication,
            )
        }
        assertThrows(ParamErrorException::class.java) {
            controller.uploadDraftImage(
                draft.id, "CONTENT", "类型错误", null,
                MockMultipartFile("file", "pixel.png", "image/jpeg", VALID_PNG), authentication,
            )
        }
        assertThrows(ParamErrorException::class.java) {
            controller.uploadDraftImage(
                draft.id, "COVER", "比例错误", null,
                MockMultipartFile("file", "pixel.png", "image/png", VALID_PNG), authentication,
            )
        }
        assertEquals(0, media.countByDraftIdAndDeletedAtIsNull(draft.id))
    }

    @Test
    fun `webp must decode completely before it is stored`() {
        val draft = content.createDraft("announcements", null, 1L)
        val authentication = adminAuthentication()
        val truncated = VALID_WEBP.copyOf(VALID_WEBP.size - 3)

        assertThrows(ParamErrorException::class.java) {
            controller.uploadDraftImage(
                draft.id, "CONTENT", "截断的 WebP", null,
                MockMultipartFile("file", "broken.webp", "image/webp", truncated), authentication,
            )
        }

        controller.uploadDraftImage(
            draft.id, "CONTENT", "有效 WebP", null,
            MockMultipartFile("file", "pixel.webp", "image/webp", VALID_WEBP), authentication,
        )
        val stored = media.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(draft.id).single()
        val path = Path.of(fileProperties.storagePath).toAbsolutePath().normalize().resolve(stored.storageName)
        try {
            assertEquals(2, stored.width)
            assertEquals(2, stored.height)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun adminAuthentication(): UsernamePasswordAuthenticationToken {
        val adminId = users.findByUsername("admin")?.id ?: error("local admin missing")
        return UsernamePasswordAuthenticationToken(adminId, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
    }

    private companion object {
        val VALID_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val PNG_SIGNATURE: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val VALID_WEBP: ByteArray = Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAfQ//73v/+BiOh/AAA=")
    }
}
