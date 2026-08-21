package top.foxball.nekomainsite.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.nekomainsite.entity.jdbc.Activity
import top.foxball.nekomainsite.entity.jdbc.ActivityKind
import top.foxball.nekomainsite.entity.jdbc.ActivityRegistration
import top.foxball.nekomainsite.entity.jdbc.ActivityStatus
import top.foxball.nekomainsite.entity.jdbc.Announcement
import top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus
import top.foxball.nekomainsite.entity.jdbc.Application
import top.foxball.nekomainsite.entity.jdbc.ApplicationKind
import top.foxball.nekomainsite.entity.jdbc.Contact
import top.foxball.nekomainsite.entity.jdbc.FeedbackStatus
import top.foxball.nekomainsite.entity.jdbc.HistoryItem
import top.foxball.nekomainsite.entity.jdbc.Idea
import top.foxball.nekomainsite.entity.jdbc.IdeaLike
import top.foxball.nekomainsite.entity.jdbc.ModerationStatus
import top.foxball.nekomainsite.entity.jdbc.PrivateFeedback
import top.foxball.nekomainsite.entity.jdbc.RegistrationStatus
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.entity.jdbc.WikiSection
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.handlder.UnauthorizedException
import top.foxball.nekomainsite.repository.ActivityRegistrationRepository
import top.foxball.nekomainsite.repository.ActivityRepository
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.ApplicationRepository
import top.foxball.nekomainsite.repository.ContactRepository
import top.foxball.nekomainsite.repository.HistoryItemRepository
import top.foxball.nekomainsite.repository.IdeaLikeRepository
import top.foxball.nekomainsite.repository.IdeaRepository
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.repository.PrivateFeedbackRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.repository.UserRepository
import top.foxball.nekomainsite.repository.WikiSectionRepository
import top.foxball.nekomainsite.shared.requirePresent
import java.time.Instant

data class ServerView(
    val id: String, val name: String, val gameplay: String, val category: String,
    val status: String, val statusLabel: String, val online: Int, val capacity: Int,
    val address: String, val version: String, val pack: String, val description: String,
    val rules: String, val icon: String, val featured: Boolean, val iconUrl: String? = null,
)

data class ActivityView(
    val id: String, val name: String, val kind: String, val status: String,
    val statusLabel: String, val serverId: String, val time: String,
    val participation: String, val description: String, val icon: String,
    val requiresPack: Boolean, val priority: Int, val iconUrl: String? = null,
    val coverImageUrl: String? = null, val blocks: JsonNode? = null,
)

data class AnnouncementView(
    val id: String, val title: String, val category: String, val categoryLabel: String,
    val publishedAt: String?, val summary: String, val content: String,
    val priority: Int, val pinned: Boolean, val coverImageUrl: String? = null,
    val blocks: JsonNode? = null,
)

data class ContactView(val id: String, val name: String, val contact: String, val responsibilities: String)
data class WikiView(
    val id: String, val title: String, val note: String, val group: String, val icon: String, val linkUrl: String?, val content: String,
    val iconUrl: String? = null, val blocks: JsonNode? = null,
)
data class HistoryView(
    val id: String, val title: String, val meta: String, val image: String?, val alt: String, val featured: Boolean, val content: String,
    val coverImageUrl: String? = null, val blocks: JsonNode? = null,
)

data class IdeaView(
    val id: Long, val nickname: String, val title: String, val category: String,
    val description: String, val status: String, val publicReply: String?,
    val relatedSlug: String?, val likes: Long, val createdAt: String,
)

data class HomeView(
    val servers: List<ServerView>, val activities: List<ActivityView>,
    val announcements: List<AnnouncementView>, val managers: List<ContactView>,
    val wiki: List<WikiView>, val history: List<HistoryView>, val totalOnline: Int,
)

data class ApplicationCommand(
    val kind: ApplicationKind, val name: String, val studentId: String?, val qq: String,
    val minecraftId: String?, val reason: String?, val participantCount: String?,
    val purpose: String?, val expectedTime: String?, val requirements: String?,
    val availableTime: String?, val skill: String?,
)

data class IdeaCommand(val title: String, val category: String, val description: String)
data class RegistrationCommand(val minecraftId: String, val qq: String)
data class FeedbackCommand(val body: String)

data class AdminApplicationView(
    val id: Long, val userId: Long?, val kind: String, val name: String, val studentId: String?,
    val qq: String, val minecraftId: String?, val reason: String?, val participantCount: String?,
    val purpose: String?, val expectedTime: String?, val requirements: String?, val availableTime: String?,
    val skill: String?, val status: String, val adminNote: String?, val createdAt: String, val updatedAt: String,
)

data class MemberApplicationView(
    val id: Long, val kind: String, val name: String, val status: String,
    val adminNote: String?, val createdAt: String, val updatedAt: String,
)

data class AdminIdeaView(
    val id: Long, val userId: Long?, val nickname: String, val title: String, val category: String,
    val description: String, val status: String, val publicReply: String?, val relatedSlug: String?,
    val createdAt: String, val updatedAt: String,
)

data class AdminRegistrationView(
    val id: Long, val activitySlug: String, val userId: Long?, val minecraftId: String, val qq: String,
    val status: String, val adminNote: String?, val createdAt: String, val updatedAt: String,
)

data class AdminFeedbackView(
    val id: Long, val userId: Long?, val body: String, val status: String, val adminNote: String?,
    val createdAt: String, val updatedAt: String,
)

@Service
@Transactional(readOnly = true)
class SiteContentService(
    private val serverRepository: ServerRepository,
    private val activityRepository: ActivityRepository,
    private val announcementRepository: AnnouncementRepository,
    private val contactRepository: ContactRepository,
    private val wikiRepository: WikiSectionRepository,
    private val historyRepository: HistoryItemRepository,
    private val applicationRepository: ApplicationRepository,
    private val ideaRepository: IdeaRepository,
    private val ideaLikeRepository: IdeaLikeRepository,
    private val registrationRepository: ActivityRegistrationRepository,
    private val feedbackRepository: PrivateFeedbackRepository,
    private val userRepository: UserRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val fileProperties: top.foxball.nekomainsite.config.FileProperties,
    private val objectMapper: ObjectMapper,
) {
    fun home(): HomeView {
        val servers = servers()
        return HomeView(
            servers = servers, activities = activities(), announcements = announcements(),
            managers = contacts(), wiki = wiki(), history = history(),
            totalOnline = servers.sumOf { it.online },
        )
    }

    fun servers(): List<ServerView> = serverRepository.findByPublishedTrueOrderByOnlineCountDescFeaturedDescNameAsc().map(::serverView)
    fun server(slug: String): ServerView = serverRepository.findBySlug(slug)?.takeIf { it.published }?.let(::serverView)
        ?: throw ResourceNotFoundException("服务器不存在")
    fun activities(): List<ActivityView> = activityRepository.findByPublishedTrueOrderByPriorityDesc()
        .sortedWith(
            compareBy<Activity> { activityDisplayRank(it) }
                .thenBy { activityStatusRank(it) }
                .thenByDescending { it.priority }
                .thenBy { it.name },
        )
        .map(::activityView)
    fun activity(slug: String): ActivityView = activityRepository.findBySlug(slug)?.takeIf { it.published }?.let(::activityView)
        ?: throw ResourceNotFoundException("活动不存在")
    fun announcements(): List<AnnouncementView> = announcementRepository
        .findByStatusOrderByPinnedDescPriorityDescPublishedAtDesc(AnnouncementStatus.PUBLISHED).map(::announcementView)
    fun announcement(slug: String): AnnouncementView = announcementRepository.findBySlug(slug)
        ?.takeIf { it.status == AnnouncementStatus.PUBLISHED }?.let(::announcementView)
        ?: throw ResourceNotFoundException("公告不存在")
    fun contacts(): List<ContactView> = contactRepository.findByPublishedTrueOrderBySortOrderAsc().map(::contactView)
    fun wiki(): List<WikiView> = wikiRepository.findByPublishedTrueOrderBySortOrderAsc().map(::wikiView)
    fun wiki(slug: String): WikiView = wikiRepository.findBySlug(slug)?.takeIf { it.published }?.let(::wikiView)
        ?: throw ResourceNotFoundException("Wiki 条目不存在")
    fun history(): List<HistoryView> = historyRepository.findByPublishedTrueOrderByFeaturedDescHappenedAtDesc().map(::historyView)
    fun history(slug: String): HistoryView = historyRepository.findBySlug(slug)?.takeIf { it.published }?.let(::historyView)
        ?: throw ResourceNotFoundException("历史活动不存在")
    fun ideas(): List<IdeaView> = ideaRepository.findByStatusNotOrderByCreatedAtDesc(ModerationStatus.HIDDEN).map(::ideaView)

    @Transactional
    fun submitApplication(userId: Long?, command: ApplicationCommand): Long {
        val user = requiredUser(userId)
        val normalized = command.copy(
            name = command.name.trim(),
            studentId = command.studentId.normalizedOptional(),
            qq = command.qq.trim(),
            minecraftId = command.minecraftId.normalizedOptional(),
            reason = command.reason.normalizedOptional(),
            participantCount = command.participantCount.normalizedOptional(),
            purpose = command.purpose.normalizedOptional(),
            expectedTime = command.expectedTime.normalizedOptional(),
            requirements = command.requirements.normalizedOptional(),
            availableTime = command.availableTime.normalizedOptional(),
            skill = command.skill.normalizedOptional(),
        )
        validateApplication(normalized)
        val persistedUserId = user.id.requirePresent("User.id")
        val application = Application(
            userId = persistedUserId, kind = normalized.kind, name = normalized.name, studentId = normalized.studentId,
            qq = normalized.qq, minecraftId = normalized.minecraftId, reason = normalized.reason,
            participantCount = normalized.participantCount, purpose = normalized.purpose,
            expectedTime = normalized.expectedTime, requirements = normalized.requirements,
            availableTime = normalized.availableTime, skill = normalized.skill,
        )
        return applicationRepository.save(application).id.requirePresent("Application.id")
    }

    @Transactional
    fun submitIdea(userId: Long?, command: IdeaCommand): Long {
        val user = requiredUser(userId)
        val persistedUserId = user.id.requirePresent("User.id")
        return ideaRepository.save(Idea(
            userId = persistedUserId, nickname = user.displayName?.ifBlank { null } ?: user.username,
            title = command.title.trim(), category = command.category.trim(), description = command.description.trim(),
        )).id.requirePresent("Idea.id")
    }

    @Transactional
    fun likeIdea(userId: Long?, ideaId: Long): Long {
        val user = requiredUser(userId)
        val idea = ideaRepository.findById(ideaId).orElseThrow { ResourceNotFoundException("建议不存在") }
        if (idea.status == ModerationStatus.HIDDEN) throw ResourceNotFoundException("建议不存在")
        val persistedIdeaId = idea.id.requirePresent("Idea.id")
        val persistedUserId = user.id.requirePresent("User.id")
        if (ideaLikeRepository.existsByIdeaIdAndUserId(persistedIdeaId, persistedUserId)) throw ConflictException("你已经点过赞了")
        ideaLikeRepository.save(IdeaLike(ideaId = persistedIdeaId, userId = persistedUserId))
        return ideaLikeRepository.countByIdeaId(persistedIdeaId)
    }

    @Transactional
    fun registerActivity(userId: Long?, activitySlug: String, command: RegistrationCommand): Long {
        val user = requiredUser(userId)
        val activity = activityRepository.findBySlug(activitySlug) ?: throw ResourceNotFoundException("活动不存在")
        if (!activity.published || activity.status == ActivityStatus.PAUSED) throw ConflictException("该活动暂不接受报名")
        val persistedUserId = user.id.requirePresent("User.id")
        if (registrationRepository.existsByActivitySlugAndUserId(activitySlug, persistedUserId)) throw ConflictException("你已经报名过这个活动了")
        return registrationRepository.save(ActivityRegistration(
            activitySlug = activitySlug, userId = persistedUserId, minecraftId = command.minecraftId.trim(), qq = command.qq.trim(),
        )).id.requirePresent("ActivityRegistration.id")
    }

    @Transactional
    fun submitFeedback(userId: Long?, command: FeedbackCommand): Long {
        val user = requiredUser(userId)
        return feedbackRepository.save(
            PrivateFeedback(userId = user.id.requirePresent("User.id"), body = command.body.trim()),
        ).id.requirePresent("PrivateFeedback.id")
    }

    fun adminApplications(): List<AdminApplicationView> = applicationRepository.findAllByOrderByCreatedAtDesc().map(::adminApplicationView)
    fun memberApplications(userId: Long?): List<MemberApplicationView> =
        applicationRepository.findAllByUserIdOrderByCreatedAtDesc(requiredUser(userId).id.requirePresent("User.id"))
            .map(::memberApplicationView)
    fun adminIdeas(): List<AdminIdeaView> = ideaRepository.findAllByOrderByCreatedAtDesc().map(::adminIdeaView)
    fun adminRegistrations(): List<AdminRegistrationView> = registrationRepository.findAllByOrderByCreatedAtDesc().map(::adminRegistrationView)
    fun adminFeedback(): List<AdminFeedbackView> = feedbackRepository.findAllByOrderByCreatedAtDesc().map(::adminFeedbackView)

    @Transactional
    fun updateIdeaStatus(id: Long, status: ModerationStatus, reply: String?): IdeaView {
        val idea = ideaRepository.findById(id).orElseThrow { ResourceNotFoundException("建议不存在") }
        idea.status = status
        idea.publicReply = reply?.trim()
        idea.updatedAt = Instant.now()
        return ideaView(ideaRepository.save(idea))
    }

    @Transactional
    fun updateApplicationStatus(id: Long, status: ModerationStatus, note: String?): AdminApplicationView {
        val application = applicationRepository.findById(id).orElseThrow { ResourceNotFoundException("申请不存在") }
        application.status = status
        application.adminNote = note?.trim()
        application.updatedAt = Instant.now()
        return adminApplicationView(applicationRepository.save(application))
    }

    @Transactional
    fun updateFeedbackStatus(id: Long, status: FeedbackStatus, note: String?): AdminFeedbackView {
        val feedback = feedbackRepository.findById(id).orElseThrow { ResourceNotFoundException("反馈不存在") }
        feedback.status = status
        feedback.adminNote = note?.trim()
        feedback.updatedAt = Instant.now()
        return adminFeedbackView(feedbackRepository.save(feedback))
    }

    @Transactional
    fun updateRegistrationStatus(id: Long, status: RegistrationStatus, note: String?): AdminRegistrationView {
        val registration = registrationRepository.findById(id).orElseThrow { ResourceNotFoundException("活动报名不存在") }
        registration.status = status
        registration.adminNote = note?.trim()
        registration.updatedAt = Instant.now()
        return adminRegistrationView(registrationRepository.save(registration))
    }

    private fun requiredUser(userId: Long?): User = userId?.let { userRepository.findById(it).orElse(null) }
        ?: throw UnauthorizedException("请先登录")

    private fun validateApplication(command: ApplicationCommand) {
        if (command.name.isBlank()) throw ParamErrorException("请填写姓名或常用昵称")
        if (!QQ_PATTERN.matches(command.qq)) throw ParamErrorException("QQ 必须是 5-12 位数字")

        when (command.kind) {
            ApplicationKind.SKIN -> {
                requireApplicationField(command.studentId, "邀请码申请必须填写学号")
                val minecraftId = requireApplicationField(command.minecraftId, "邀请码申请必须填写 Minecraft ID")
                if (!MINECRAFT_ID_PATTERN.matches(minecraftId)) {
                    throw ParamErrorException("Minecraft ID 必须是 3-16 位字母、数字或下划线")
                }
            }
            ApplicationKind.SERVER -> {
                requireApplicationField(command.participantCount, "开服申请必须填写参与人数")
                requireApplicationField(command.purpose, "开服申请必须填写服务器用途")
                requireApplicationField(command.expectedTime, "开服申请必须填写预计时间")
                requireApplicationField(command.requirements, "开服申请必须填写插件或整合包需求")
            }
            ApplicationKind.DUTY -> {
                requireApplicationField(command.availableTime, "值班申请必须填写可值班时间")
                requireApplicationField(command.skill, "值班申请必须填写擅长内容")
            }
        }
    }

    private fun requireApplicationField(value: String?, message: String): String =
        value?.takeIf(String::isNotBlank) ?: throw ParamErrorException(message)

    private fun String?.normalizedOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun serverView(item: Server) = ServerView(
        id = item.slug, name = item.name, gameplay = item.gameplay, category = item.category.name.lowercase(),
        status = item.status.name.lowercase(), statusLabel = item.statusLabel, online = item.onlineCount,
        capacity = item.capacity, address = item.address, version = item.version, pack = item.pack,
        description = item.description, rules = item.rules, icon = item.icon, featured = item.featured,
        iconUrl = mediaUrl(item.iconMediaId),
    )

    private fun activityView(item: Activity) = ActivityView(
        id = item.slug, name = item.name, kind = item.kind.name.lowercase().replace("_", "-"),
        status = item.status.name.lowercase(), statusLabel = item.statusLabel, serverId = item.serverSlug,
        time = item.timeText, participation = item.participation, description = item.description,
        icon = item.icon, requiresPack = item.requiresPack, priority = item.priority,
        iconUrl = mediaUrl(item.iconMediaId), coverImageUrl = mediaUrl(item.coverMediaId), blocks = blockView(item.contentBlocks, item.description),
    )

    private fun announcementView(item: Announcement) = AnnouncementView(
        id = item.slug, title = item.title, category = item.category, categoryLabel = item.categoryLabel,
        publishedAt = item.publishedAt?.toString(), summary = item.summary, content = item.content,
        priority = item.priority, pinned = item.pinned, coverImageUrl = mediaUrl(item.coverMediaId), blocks = blockView(item.contentBlocks, item.content),
    )

    private fun contactView(item: Contact) = ContactView(item.slug, item.name, item.contact, item.responsibilities)
    private fun wikiView(item: WikiSection) = WikiView(item.slug, item.title, item.summary, item.groupName, item.icon, item.linkUrl, item.content, mediaUrl(item.iconMediaId), blockView(item.contentBlocks, item.content))
    private fun historyView(item: HistoryItem) = HistoryView(item.slug, item.title, item.meta, item.imageUrl, item.altText, item.featured, item.content, mediaUrl(item.coverMediaId), blockView(item.contentBlocks, item.content))

    private fun mediaUrl(id: Long?): String? = id?.let { mediaAssetRepository.findById(it).orElse(null) }
        ?.takeIf { it.deletedAt == null }
        ?.let { "${fileProperties.baseUrl.trimEnd('/')}/api/public/media/${it.storageName}" }

    private fun blockView(raw: String, fallback: String): JsonNode {
        val parsed = runCatching { objectMapper.readTree(raw) }.getOrNull()
        if (parsed != null && parsed.isArray && parsed.size() > 0) return parsed
        return objectMapper.readTree(objectMapper.writeValueAsString(listOf(mapOf("type" to "paragraph", "text" to fallback))))
    }
    private fun activityDisplayRank(activity: Activity): Int = when {
        activity.kind == ActivityKind.WEEKLY && activity.status != ActivityStatus.PAUSED -> 0
        activity.kind == ActivityKind.LONG_TERM -> 1
        activity.kind == ActivityKind.LIMITED -> 2
        else -> 3
    }

    private fun activityStatusRank(activity: Activity): Int = when (activity.status) {
        ActivityStatus.ACTIVE, ActivityStatus.ONGOING -> 0
        ActivityStatus.UPCOMING -> 1
        ActivityStatus.PAUSED -> 2
    }

    private companion object {
        val QQ_PATTERN = Regex("\\d{5,12}")
        val MINECRAFT_ID_PATTERN = Regex("[A-Za-z0-9_]{3,16}")
    }

    private fun ideaView(item: Idea): IdeaView {
        val id = item.id.requirePresent("Idea.id")
        return IdeaView(
            id = id, nickname = item.nickname, title = item.title, category = item.category,
            description = item.description, status = item.status.name, publicReply = item.publicReply,
            relatedSlug = item.relatedSlug, likes = ideaLikeRepository.countByIdeaId(id), createdAt = item.createdAt.toString(),
        )
    }

    private fun adminApplicationView(item: Application) = AdminApplicationView(
        item.id.requirePresent("Application.id"), item.userId, item.kind.name, item.name, item.studentId, item.qq, item.minecraftId,
        item.reason, item.participantCount, item.purpose, item.expectedTime, item.requirements,
        item.availableTime, item.skill, item.status.name, item.adminNote, item.createdAt.toString(), item.updatedAt.toString(),
    )

    private fun memberApplicationView(item: Application) = MemberApplicationView(
        item.id.requirePresent("Application.id"), item.kind.name, item.name, item.status.name,
        item.adminNote, item.createdAt.toString(), item.updatedAt.toString(),
    )

    private fun adminIdeaView(item: Idea) = AdminIdeaView(
        item.id.requirePresent("Idea.id"), item.userId, item.nickname, item.title, item.category, item.description, item.status.name,
        item.publicReply, item.relatedSlug, item.createdAt.toString(), item.updatedAt.toString(),
    )

    private fun adminRegistrationView(item: ActivityRegistration) = AdminRegistrationView(
        item.id.requirePresent("ActivityRegistration.id"), item.activitySlug, item.userId, item.minecraftId, item.qq, item.status.name,
        item.adminNote, item.createdAt.toString(), item.updatedAt.toString(),
    )

    private fun adminFeedbackView(item: PrivateFeedback) = AdminFeedbackView(
        item.id.requirePresent("PrivateFeedback.id"), item.userId, item.body, item.status.name, item.adminNote,
        item.createdAt.toString(), item.updatedAt.toString(),
    )
}
