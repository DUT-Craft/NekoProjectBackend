package top.foxball.nekomainsite.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.nekomainsite.entity.jdbc.Activity
import top.foxball.nekomainsite.entity.jdbc.ActivityRegistration
import top.foxball.nekomainsite.entity.jdbc.Announcement
import top.foxball.nekomainsite.entity.jdbc.Application
import top.foxball.nekomainsite.entity.jdbc.Contact
import top.foxball.nekomainsite.entity.jdbc.FeedbackStatus
import top.foxball.nekomainsite.entity.jdbc.HistoryItem
import top.foxball.nekomainsite.entity.jdbc.Idea
import top.foxball.nekomainsite.entity.jdbc.IdeaLike
import top.foxball.nekomainsite.entity.jdbc.ModerationStatus
import top.foxball.nekomainsite.entity.jdbc.PrivateFeedback
import top.foxball.nekomainsite.entity.jdbc.RegistrationStatus
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.WikiSection
import top.foxball.nekomainsite.entity.jdbc.ContentDraft
import top.foxball.nekomainsite.entity.jdbc.PreviousContentVersion
import top.foxball.nekomainsite.entity.jdbc.MediaAsset

interface ServerRepository : JpaRepository<Server, Long> {
    fun findBySlug(slug: String): Server?
    fun countByPublishedTrue(): Long
    fun countByPublishedTrueAndMaintenanceTrue(): Long
    fun findByPublishedTrueOrderByOnlineCountDescFeaturedDescNameAsc(): List<Server>
}

interface ActivityRepository : JpaRepository<Activity, Long> {
    fun findBySlug(slug: String): Activity?
    fun existsByServerSlug(serverSlug: String): Boolean
    fun findByPublishedTrueOrderByPriorityDesc(): List<Activity>
}

interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    fun findByStatusOrderByPinnedDescPriorityDescPublishedAtDesc(status: top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus): List<Announcement>
    fun countByStatus(status: top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus): Long
    fun findBySlug(slug: String): Announcement?
}

interface ContactRepository : JpaRepository<Contact, Long> {
    fun findBySlug(slug: String): Contact?
    fun findByPublishedTrueOrderBySortOrderAsc(): List<Contact>
    fun findAllByOrderBySortOrderAsc(): List<Contact>
}

interface WikiSectionRepository : JpaRepository<WikiSection, Long> {
    fun findBySlug(slug: String): WikiSection?
    fun findByPublishedTrueOrderBySortOrderAsc(): List<WikiSection>
}

interface HistoryItemRepository : JpaRepository<HistoryItem, Long> {
    fun findBySlug(slug: String): HistoryItem?
    fun findByPublishedTrueOrderByFeaturedDescHappenedAtDesc(): List<HistoryItem>
}

interface ApplicationRepository : JpaRepository<Application, Long> {
    fun findByStatusOrderByCreatedAtDesc(status: ModerationStatus): List<Application>
    fun countByStatus(status: ModerationStatus): Long
    fun findAllByOrderByCreatedAtDesc(): List<Application>
}

interface IdeaRepository : JpaRepository<Idea, Long> {
    fun findByStatusNotOrderByCreatedAtDesc(status: ModerationStatus): List<Idea>
    fun findByStatusOrderByCreatedAtDesc(status: ModerationStatus): List<Idea>
    fun countByStatus(status: ModerationStatus): Long
    fun findAllByOrderByCreatedAtDesc(): List<Idea>
}

interface IdeaLikeRepository : JpaRepository<IdeaLike, Long> {
    fun existsByIdeaIdAndUserId(ideaId: Long, userId: Long): Boolean
    fun countByIdeaId(ideaId: Long): Long
}

interface ActivityRegistrationRepository : JpaRepository<ActivityRegistration, Long> {
    fun existsByActivitySlugAndUserId(activitySlug: String, userId: Long): Boolean
    fun existsByActivitySlug(activitySlug: String): Boolean
    fun findAllByOrderByCreatedAtDesc(): List<ActivityRegistration>
    fun findByStatusOrderByCreatedAtDesc(status: RegistrationStatus): List<ActivityRegistration>
    fun countByStatus(status: RegistrationStatus): Long
}

interface PrivateFeedbackRepository : JpaRepository<PrivateFeedback, Long> {
    fun findByStatusOrderByCreatedAtDesc(status: FeedbackStatus): List<PrivateFeedback>
    fun countByStatus(status: FeedbackStatus): Long
    fun findAllByOrderByCreatedAtDesc(): List<PrivateFeedback>
}

interface AuditLogRepository : JpaRepository<top.foxball.nekomainsite.entity.jdbc.AuditLog, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<top.foxball.nekomainsite.entity.jdbc.AuditLog>
}

interface ContentDraftRepository : JpaRepository<ContentDraft, String> {
    fun findByResourceTypeAndResourceId(resourceType: String, resourceId: Long): ContentDraft?
    fun findAllByResourceTypeOrderByUpdatedAtDesc(resourceType: String): List<ContentDraft>
}

interface PreviousContentVersionRepository : JpaRepository<PreviousContentVersion, Long> {
    fun findByResourceTypeAndResourceId(resourceType: String, resourceId: Long): PreviousContentVersion?
}

interface MediaAssetRepository : JpaRepository<MediaAsset, Long> {
    fun findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(draftId: String): List<MediaAsset>
    fun findAllByResourceTypeAndResourceIdAndDeletedAtIsNullOrderByCreatedAtAsc(resourceType: String, resourceId: Long): List<MediaAsset>
    fun findByStorageNameAndDeletedAtIsNull(storageName: String): MediaAsset?
    fun countByDraftIdAndDeletedAtIsNull(draftId: String): Long
    fun findAllByDeletedAtBeforeAndDeletedAtIsNotNull(cutoff: java.time.Instant): List<MediaAsset>
}
