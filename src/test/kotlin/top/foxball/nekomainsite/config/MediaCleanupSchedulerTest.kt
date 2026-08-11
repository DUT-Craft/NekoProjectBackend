package top.foxball.nekomainsite.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito
import top.foxball.nekomainsite.entity.jdbc.MediaAsset
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.service.ContentManagementService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

class MediaCleanupSchedulerTest {
    @TempDir
    lateinit var storage: Path

    @Test
    fun `one failed file does not delete its record or abort later cleanup`() {
        val repository = Mockito.mock(MediaAssetRepository::class.java)
        val content = Mockito.mock(ContentManagementService::class.java)
        val blocked = expiredMedia(1L, "blocked.png")
        val removable = expiredMedia(2L, "removable.png")
        val anyInstant = Mockito.any(Instant::class.java) ?: Instant.EPOCH
        Mockito.`when`(repository.findAllByDeletedAtBeforeAndDeletedAtIsNotNull(anyInstant))
            .thenReturn(listOf(blocked, removable))
        Mockito.`when`(content.isMediaReferenced(1L)).thenReturn(false)
        Mockito.`when`(content.isMediaReferenced(2L)).thenReturn(false)

        val blockedPath = Files.createDirectory(storage.resolve(blocked.storageName))
        Files.writeString(blockedPath.resolve("child"), "keeps the directory non-empty")
        val removablePath = Files.writeString(storage.resolve(removable.storageName), "image")

        MediaCleanupScheduler(FileProperties(storagePath = storage.toString()), repository, content).cleanup()

        Mockito.verify(repository, Mockito.never()).delete(blocked)
        Mockito.verify(repository).delete(removable)
        assertTrue(Files.exists(blockedPath))
        assertTrue(Files.notExists(removablePath))
    }

    private fun expiredMedia(id: Long, storageName: String) = MediaAsset(
        id = id,
        resourceType = "ANNOUNCEMENT",
        purpose = "CONTENT",
        originalName = storageName,
        storageName = storageName,
        mimeType = "image/png",
        sizeBytes = 5,
        deletedAt = Instant.now().minus(8, ChronoUnit.DAYS),
    )
}
