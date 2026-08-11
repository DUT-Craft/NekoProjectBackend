package top.foxball.nekomainsite.config

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.service.ContentManagementService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class MediaCleanupScheduler(
    private val fileProperties: FileProperties,
    private val mediaRepository: MediaAssetRepository,
    private val contentManagementService: ContentManagementService,
) {
    @Scheduled(fixedDelay = 86_400_000, initialDelay = 86_400_000)
    @Transactional
    fun cleanup() {
        val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
        val root = Path.of(fileProperties.storagePath).toAbsolutePath().normalize()
        mediaRepository.findAllByDeletedAtBeforeAndDeletedAtIsNotNull(cutoff).forEach { media ->
            if (media.id?.let(contentManagementService::isMediaReferenced) != true) {
                val target = root.resolve(media.storageName).normalize()
                if (target.parent != root) {
                    log.warn("Skipping media cleanup outside storage root: {}", media.storageName)
                    return@forEach
                }
                try {
                    Files.deleteIfExists(target)
                    mediaRepository.delete(media)
                } catch (error: Exception) {
                    log.warn("Could not remove expired media {}", media.storageName, error)
                }
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MediaCleanupScheduler::class.java)
    }
}
