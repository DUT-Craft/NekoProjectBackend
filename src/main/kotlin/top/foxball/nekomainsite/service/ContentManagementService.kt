package top.foxball.nekomainsite.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import top.foxball.nekomainsite.entity.jdbc.Activity
import top.foxball.nekomainsite.entity.jdbc.ActivityKind
import top.foxball.nekomainsite.entity.jdbc.ActivityStatus
import top.foxball.nekomainsite.entity.jdbc.Announcement
import top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus
import top.foxball.nekomainsite.entity.jdbc.AuditLog
import top.foxball.nekomainsite.entity.jdbc.ContentDraft
import top.foxball.nekomainsite.entity.jdbc.HistoryItem
import top.foxball.nekomainsite.entity.jdbc.MediaAsset
import top.foxball.nekomainsite.entity.jdbc.PreviousContentVersion
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerCategory
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.entity.jdbc.WikiSection
import top.foxball.nekomainsite.config.FileProperties
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.repository.ActivityRepository
import top.foxball.nekomainsite.repository.ActivityRegistrationRepository
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.ContentDraftRepository
import top.foxball.nekomainsite.repository.HistoryItemRepository
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.repository.PreviousContentVersionRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.repository.WikiSectionRepository
import top.foxball.nekomainsite.shared.requirePresent
import java.time.Instant

data class AdminContentSummary(
    val id: Long?,
    val slug: String,
    val title: String,
    val status: String,
    val published: Boolean,
    val updatedAt: String,
    val draftId: String?,
    val hasUnpublishedChanges: Boolean,
    val onlineCount: Int? = null,
    val capacity: Int? = null,
    val maintenance: Boolean? = null,
    val lastCheckedAt: String? = null,
)

data class AdminDraftView(
    val id: String,
    val resourceType: String,
    val resourceId: Long?,
    val version: Long,
    val payload: JsonNode,
    val media: List<AdminMediaView>,
    val hasPreviousVersion: Boolean,
    val updatedAt: String,
)

data class AdminMediaView(
    val id: Long,
    val url: String,
    val purpose: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val altText: String?,
    val caption: String?,
)

@Service
class ContentManagementService(
    private val serverRepository: ServerRepository,
    private val activityRepository: ActivityRepository,
    private val registrationRepository: ActivityRegistrationRepository,
    private val announcementRepository: AnnouncementRepository,
    private val wikiRepository: WikiSectionRepository,
    private val historyRepository: HistoryItemRepository,
    private val draftRepository: ContentDraftRepository,
    private val previousRepository: PreviousContentVersionRepository,
    private val mediaRepository: MediaAssetRepository,
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper,
    private val fileProperties: FileProperties,
) {
    private val allowedBlockTypes = setOf("heading", "paragraph", "bulletList", "stepList", "image", "gallery", "callout", "code", "link", "action")
    private val allowedInlineNodeTypes = setOf("text", "hardBreak")
    private val allowedInlineMarkTypes = setOf("bold", "italic", "underline", "strike", "code", "link")
    private val allowedTextAlignments = setOf("left", "center", "right", "justify")
    private val allowedCalloutTones = setOf("info", "warning")

    @Transactional(readOnly = true)
    fun list(resource: String): List<AdminContentSummary> {
        val type = resourceType(resource)
        val publishedContent = when (type) {
            "SERVER" -> serverRepository.findAll().map {
                summary(
                    type, it.id, it.slug, it.name,
                    if (it.published) it.status.name else "HIDDEN",
                    it.published, it.updatedAt,
                    onlineCount = it.onlineCount,
                    capacity = it.capacity,
                    maintenance = it.maintenance,
                    lastCheckedAt = it.lastCheckedAt?.toString(),
                )
            }
            "ACTIVITY" -> activityRepository.findAll().map { summary(type, it.id, it.slug, it.name, if (it.published) it.status.name else "HIDDEN", it.published, it.updatedAt) }
            "ANNOUNCEMENT" -> announcementRepository.findAll().map { summary(type, it.id, it.slug, it.title, it.status.name, it.status == AnnouncementStatus.PUBLISHED, it.updatedAt) }
            "WIKI" -> wikiRepository.findAll().map { summary(type, it.id, it.slug, it.title, if (it.published) "PUBLISHED" else "HIDDEN", it.published, it.updatedAt) }
            "HISTORY" -> historyRepository.findAll().map { summary(type, it.id, it.slug, it.title, if (it.published) "PUBLISHED" else "HIDDEN", it.published, it.updatedAt) }
            else -> error("unsupported content type")
        }
        val newDrafts = draftRepository.findAllByResourceTypeOrderByUpdatedAtDesc(type)
            .filter { it.resourceId == null }
            .map(::draftSummary)
        return newDrafts + publishedContent.sortedByDescending { it.updatedAt }
    }

    @Transactional
    fun createDraft(resource: String, resourceId: Long?, operatorId: Long?): AdminDraftView {
        val type = resourceType(resource)
        if (resourceId != null) {
            draftRepository.findByResourceTypeAndResourceId(type, resourceId)?.let { return view(it) }
        }
        val payload = if (resourceId == null) blankPayload(type) else currentPayload(type, resourceId)
        val now = Instant.now()
        val draft = draftRepository.save(ContentDraft(
            resourceType = type,
            resourceId = resourceId,
            payloadJson = objectMapper.writeValueAsString(payload),
            createdBy = operatorId,
            updatedBy = operatorId,
            createdAt = now,
            updatedAt = now,
        ))
        audit(operatorId, type, resourceId?.toString() ?: draft.id, "DRAFT_CREATE")
        return view(draft)
    }

    @Transactional(readOnly = true)
    fun draft(id: String): AdminDraftView = view(draftRepository.findById(id).orElseThrow { ResourceNotFoundException("草稿不存在") })

    @Transactional
    fun saveDraft(id: String, expectedVersion: Long, payload: JsonNode, operatorId: Long?): AdminDraftView {
        val draft = draftRepository.findById(id).orElseThrow { ResourceNotFoundException("草稿不存在") }
        if (draft.version != expectedVersion) throw ConflictException("草稿已被其他管理员更新，请重新载入")
        validatePayload(draft.resourceType, payload, requireComplete = false)
        val payloadJson = objectMapper.writeValueAsString(payload)
        if (payloadJson.toByteArray(Charsets.UTF_8).size > MAX_DRAFT_BYTES) throw ParamErrorException("草稿内容过大")
        draft.payloadJson = payloadJson
        draft.version += 1
        draft.updatedBy = operatorId
        draft.updatedAt = Instant.now()
        return view(draftRepository.saveAndFlush(draft))
    }

    @Transactional
    fun publish(id: String, expectedVersion: Long, operatorId: Long?): AdminDraftView {
        val draft = draftRepository.findById(id).orElseThrow { ResourceNotFoundException("草稿不存在") }
        if (draft.version != expectedVersion) throw ConflictException("草稿已被其他管理员更新，请重新载入")
        val payload = objectMapper.readTree(draft.payloadJson)
        validatePayload(draft.resourceType, payload)
        val type = draft.resourceType
        val resourceId = draft.resourceId
        ensureSlugAvailable(type, resourceId, payload.path("slug").asString(""))
        val referencedMedia = validateReferencedMedia(type, draft.id, resourceId, payload)
        if (resourceId != null) {
            previousRepository.save(previousRepository.findByResourceTypeAndResourceId(type, resourceId)
                ?.apply { payloadJson = currentPayloadJson(type, resourceId); savedBy = operatorId; savedAt = Instant.now() }
                ?: PreviousContentVersion(resourceType = type, resourceId = resourceId, payloadJson = currentPayloadJson(type, resourceId), savedBy = operatorId))
        }
        val savedId = applyPayload(type, resourceId, payload)
        draft.resourceId = savedId
        draft.payloadJson = currentPayloadJson(type, savedId)
        draft.version += 1
        draft.updatedBy = operatorId
        draft.updatedAt = Instant.now()
        referencedMedia.values.forEach { media ->
            if (media.draftId == id) {
                media.draftId = null
                media.resourceId = savedId
            }
            media.deletedAt = null
            mediaRepository.save(media)
        }
        val referencedMediaIds = referencedMedia.keys
        mediaRepository.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(id)
            .filter { it.id !in referencedMediaIds }
            .forEach {
                it.deletedAt = Instant.now()
                mediaRepository.save(it)
            }
        mediaRepository.findAllByResourceTypeAndResourceIdAndDeletedAtIsNullOrderByCreatedAtAsc(type, savedId)
            .filter { it.id !in referencedMediaIds }
            .forEach {
                it.deletedAt = Instant.now()
                mediaRepository.save(it)
        }
        audit(operatorId, type, savedId.toString(), "PUBLISH")
        return view(draftRepository.saveAndFlush(draft))
    }

    @Transactional
    fun restorePrevious(resource: String, resourceId: Long, operatorId: Long?): AdminDraftView {
        val type = resourceType(resource)
        val previous = previousRepository.findByResourceTypeAndResourceId(type, resourceId)
            ?: throw ResourceNotFoundException("没有可恢复的上一版本")
        val existing = draftRepository.findByResourceTypeAndResourceId(type, resourceId)
        val draft = existing ?: ContentDraft(resourceType = type, resourceId = resourceId, createdBy = operatorId, updatedBy = operatorId)
        draft.payloadJson = previous.payloadJson
        draft.version += 1
        draft.updatedBy = operatorId
        draft.updatedAt = Instant.now()
        val payload = objectMapper.readTree(previous.payloadJson)
        validateReferencedMedia(type, draft.id, resourceId, payload).values.forEach { media ->
            if (media.deletedAt != null) {
                media.deletedAt = null
                mediaRepository.save(media)
            }
        }
        audit(operatorId, type, resourceId.toString(), "RESTORE_PREVIOUS")
        return view(draftRepository.saveAndFlush(draft))
    }

    @Transactional
    fun unpublish(resource: String, resourceId: Long, operatorId: Long?): Map<String, Any?> {
        val type = resourceType(resource)
        when (type) {
            "SERVER" -> serverRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("服务器不存在") }.apply { published = false; updatedAt = Instant.now(); serverRepository.save(this) }
            "ACTIVITY" -> activityRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("活动不存在") }.apply { published = false; updatedAt = Instant.now(); activityRepository.save(this) }
            "ANNOUNCEMENT" -> announcementRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("公告不存在") }.apply { status = AnnouncementStatus.HIDDEN; updatedAt = Instant.now(); announcementRepository.save(this) }
            "WIKI" -> wikiRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("Wiki 条目不存在") }.apply { published = false; updatedAt = Instant.now(); wikiRepository.save(this) }
            "HISTORY" -> historyRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("历史活动不存在") }.apply { published = false; updatedAt = Instant.now(); historyRepository.save(this) }
        }
        audit(operatorId, type, resourceId.toString(), "UNPUBLISH")
        return mapOf("id" to resourceId, "published" to false)
    }

    @Transactional
    fun deleteResource(resource: String, resourceId: Long, operatorId: Long?): Map<String, Any?> {
        val type = resourceType(resource)
        when (type) {
            "SERVER" -> {
                val item = serverRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("服务器不存在") }
                if (item.published) throw ConflictException("请先下线服务器，再永久删除")
                if (activityRepository.existsByServerSlug(item.slug)) throw ConflictException("仍有活动关联这台服务器，无法永久删除")
                serverRepository.delete(item)
            }
            "ACTIVITY" -> {
                val item = activityRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("活动不存在") }
                if (item.published) throw ConflictException("请先下线活动，再永久删除")
                if (registrationRepository.existsByActivitySlug(item.slug)) throw ConflictException("活动已有报名记录，只能保留下线状态")
                activityRepository.delete(item)
            }
            "ANNOUNCEMENT" -> {
                val item = announcementRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("公告不存在") }
                if (item.status != AnnouncementStatus.HIDDEN) throw ConflictException("请先下线公告，再永久删除")
                announcementRepository.delete(item)
            }
            "WIKI" -> {
                val item = wikiRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("Wiki 条目不存在") }
                if (item.published) throw ConflictException("请先下线 Wiki 条目，再永久删除")
                wikiRepository.delete(item)
            }
            "HISTORY" -> {
                val item = historyRepository.findById(resourceId).orElseThrow { ResourceNotFoundException("历史活动不存在") }
                if (item.published) throw ConflictException("请先下线历史活动，再永久删除")
                historyRepository.delete(item)
            }
        }

        val now = Instant.now()
        draftRepository.findByResourceTypeAndResourceId(type, resourceId)?.let { draft ->
            mediaRepository.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(draft.id).forEach {
                it.deletedAt = now
                mediaRepository.save(it)
            }
            draftRepository.delete(draft)
        }
        previousRepository.findByResourceTypeAndResourceId(type, resourceId)?.let(previousRepository::delete)
        mediaRepository.findAllByResourceTypeAndResourceIdAndDeletedAtIsNullOrderByCreatedAtAsc(type, resourceId).forEach {
            it.deletedAt = now
            mediaRepository.save(it)
        }
        audit(operatorId, type, resourceId.toString(), "DELETE")
        return mapOf("id" to resourceId, "deleted" to true)
    }

    @Transactional
    fun deleteDraft(id: String, operatorId: Long?): Map<String, Any?> {
        val draft = draftRepository.findById(id).orElseThrow { ResourceNotFoundException("草稿不存在") }
        mediaRepository.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(id).forEach { it.deletedAt = Instant.now(); mediaRepository.save(it) }
        draftRepository.delete(draft)
        audit(operatorId, draft.resourceType, id, "DRAFT_DELETE")
        return mapOf("id" to id, "deleted" to true)
    }

    @Transactional(readOnly = true)
    fun isMediaReferenced(mediaId: Long): Boolean {
        if (serverRepository.findAll().any { it.iconMediaId == mediaId }) return true
        if (activityRepository.findAll().any { it.iconMediaId == mediaId || it.coverMediaId == mediaId || blocksReference(it.contentBlocks, mediaId) }) return true
        if (announcementRepository.findAll().any { it.coverMediaId == mediaId || blocksReference(it.contentBlocks, mediaId) }) return true
        if (wikiRepository.findAll().any { it.iconMediaId == mediaId || blocksReference(it.contentBlocks, mediaId) }) return true
        if (historyRepository.findAll().any { it.coverMediaId == mediaId || blocksReference(it.contentBlocks, mediaId) }) return true
        if (previousRepository.findAll().any { payloadReferences(it.payloadJson, mediaId) }) return true
        return draftRepository.findAll().any { payloadReferences(it.payloadJson, mediaId) }
    }

    @Transactional(readOnly = true)
    fun isMediaPubliclyReadable(media: MediaAsset): Boolean {
        if (media.deletedAt != null || media.draftId != null) return false
        val resourceId = media.resourceId ?: return false
        return when (media.resourceType) {
            "SERVER" -> serverRepository.findById(resourceId).orElse(null)?.published == true
            "ACTIVITY" -> activityRepository.findById(resourceId).orElse(null)?.published == true
            "ANNOUNCEMENT" -> announcementRepository.findById(resourceId).orElse(null)?.status == AnnouncementStatus.PUBLISHED
            "WIKI" -> wikiRepository.findById(resourceId).orElse(null)?.published == true
            "HISTORY" -> historyRepository.findById(resourceId).orElse(null)?.published == true
            else -> false
        }
    }

    private fun blocksReference(raw: String, mediaId: Long): Boolean {
        val blocks = runCatching { objectMapper.readTree(raw) }.getOrNull() ?: return false
        val wrapper = objectMapper.readTree(objectMapper.writeValueAsString(mapOf("blocks" to blocks)))
        return mediaId in referencedMediaIds(wrapper)
    }

    private fun payloadReferences(raw: String, mediaId: Long): Boolean =
        runCatching { mediaId in referencedMediaIds(objectMapper.readTree(raw)) }.getOrDefault(false)

    private fun view(draft: ContentDraft): AdminDraftView = AdminDraftView(
        id = draft.id,
        resourceType = draft.resourceType,
        resourceId = draft.resourceId,
        version = draft.version,
        payload = objectMapper.readTree(draft.payloadJson),
        media = (mediaRepository.findAllByDraftIdAndDeletedAtIsNullOrderByCreatedAtAsc(draft.id) +
            (draft.resourceId?.let { mediaRepository.findAllByResourceTypeAndResourceIdAndDeletedAtIsNullOrderByCreatedAtAsc(draft.resourceType, it) } ?: emptyList()))
            .distinctBy { it.id }
            .map(::mediaView),
        hasPreviousVersion = draft.resourceId?.let { previousRepository.findByResourceTypeAndResourceId(draft.resourceType, it) != null } ?: false,
        updatedAt = draft.updatedAt.toString(),
    )

    private fun mediaView(item: MediaAsset) = AdminMediaView(item.id.requirePresent("MediaAsset.id"), "${fileProperties.baseUrl.trimEnd('/')}/api/public/media/${item.storageName}", item.purpose, item.originalName, item.mimeType, item.sizeBytes, item.width, item.height, item.altText, item.caption)

    private fun summary(
        type: String,
        id: Long?,
        slug: String,
        title: String,
        status: String,
        published: Boolean,
        updatedAt: Instant,
        onlineCount: Int? = null,
        capacity: Int? = null,
        maintenance: Boolean? = null,
        lastCheckedAt: String? = null,
    ): AdminContentSummary {
        val draft = id?.let { draftRepository.findByResourceTypeAndResourceId(type, it) }
        val hasUnpublishedChanges = draft?.let {
            contentFingerprint(type, currentPayloadJson(type, id)) != contentFingerprint(type, it.payloadJson)
        } ?: false
        return AdminContentSummary(
            id, slug, title, status, published, updatedAt.toString(), draft?.id, hasUnpublishedChanges,
            onlineCount, capacity, maintenance, lastCheckedAt,
        )
    }

    /** 去掉发布状态等生命周期字段后的内容指纹，用于判断草稿是否有真实内容差异。 */
    private fun contentFingerprint(type: String, payloadJson: String): String {
        val node = runCatching { objectMapper.readTree(payloadJson) }.getOrNull() ?: return payloadJson
        val objectNode = node as? ObjectNode ?: return node.toString()
        val lifecycleFields = if (type == "ANNOUNCEMENT") listOf("status") else listOf("published")
        lifecycleFields.forEach(objectNode::remove)
        return objectNode.toString()
    }

    private fun draftSummary(draft: ContentDraft): AdminContentSummary {
        val payload = runCatching { objectMapper.readTree(draft.payloadJson) }.getOrNull()
        val slug = payload?.path("slug")?.asString("")?.trim().orEmpty()
        val title = payload?.path(if (draft.resourceType in setOf("SERVER", "ACTIVITY")) "name" else "title")
            ?.asString("")?.trim().orEmpty().ifBlank { "未命名草稿" }
        return AdminContentSummary(null, slug, title, "DRAFT", false, draft.updatedAt.toString(), draft.id, true)
    }

    private fun resourceType(resource: String): String = when (resource.lowercase()) {
        "servers", "server" -> "SERVER"
        "activities", "activity" -> "ACTIVITY"
        "announcements", "announcement" -> "ANNOUNCEMENT"
        "wiki", "wikis" -> "WIKI"
        "history", "histories" -> "HISTORY"
        else -> throw ParamErrorException("不支持的内容类型")
    }

    private fun blankPayload(type: String): JsonNode = objectMapper.readTree(objectMapper.writeValueAsString(when (type) {
        "SERVER" -> mapOf("slug" to "", "name" to "", "gameplay" to "", "category" to "PERMANENT", "address" to "", "version" to "", "pack" to "", "description" to "", "rules" to "", "icon" to "server", "iconMediaId" to null, "featured" to false, "published" to false)
        "ACTIVITY" -> mapOf("slug" to "", "name" to "", "kind" to "LONG_TERM", "status" to "UPCOMING", "statusLabel" to "即将开始", "serverSlug" to "", "timeText" to "", "participation" to "", "description" to "", "icon" to "event", "iconMediaId" to null, "coverMediaId" to null, "blocks" to emptyList<Any>(), "requiresPack" to false, "priority" to 0, "published" to false)
        "ANNOUNCEMENT" -> mapOf("slug" to "", "title" to "", "category" to "club", "categoryLabel" to "社团通知", "summary" to "", "content" to "", "coverMediaId" to null, "blocks" to emptyList<Any>(), "priority" to 0, "pinned" to false, "status" to "DRAFT")
        "WIKI" -> mapOf("slug" to "", "title" to "", "summary" to "", "groupName" to "入服指南", "content" to "", "icon" to "guide", "iconMediaId" to null, "blocks" to emptyList<Any>(), "linkUrl" to null, "sortOrder" to 0, "published" to false)
        else -> mapOf("slug" to "", "title" to "", "meta" to "", "imageUrl" to null, "coverMediaId" to null, "altText" to "活动记录", "featured" to false, "content" to "", "blocks" to emptyList<Any>(), "happenedAt" to null, "published" to false)
    }))

    private fun currentPayload(type: String, id: Long): JsonNode = objectMapper.readTree(currentPayloadJson(type, id))

    private fun currentPayloadJson(type: String, id: Long): String = objectMapper.writeValueAsString(currentPayloadMap(type, id))

    private fun currentPayloadMap(type: String, id: Long): Map<String, Any?> = when (type) {
        "SERVER" -> serverRepository.findById(id).orElseThrow { ResourceNotFoundException("服务器不存在") }.let { mapOf("slug" to it.slug, "name" to it.name, "gameplay" to it.gameplay, "category" to it.category.name, "address" to it.address, "version" to it.version, "pack" to it.pack, "description" to it.description, "rules" to it.rules, "icon" to it.icon, "iconMediaId" to it.iconMediaId, "featured" to it.featured, "published" to it.published) }
        "ACTIVITY" -> activityRepository.findById(id).orElseThrow { ResourceNotFoundException("活动不存在") }.let { mapOf("slug" to it.slug, "name" to it.name, "kind" to it.kind.name, "status" to it.status.name, "statusLabel" to it.statusLabel, "serverSlug" to it.serverSlug, "timeText" to it.timeText, "participation" to it.participation, "description" to it.description, "icon" to it.icon, "iconMediaId" to it.iconMediaId, "coverMediaId" to it.coverMediaId, "blocks" to blocks(it.contentBlocks), "requiresPack" to it.requiresPack, "priority" to it.priority, "published" to it.published) }
        "ANNOUNCEMENT" -> announcementRepository.findById(id).orElseThrow { ResourceNotFoundException("公告不存在") }.let { mapOf("slug" to it.slug, "title" to it.title, "category" to it.category, "categoryLabel" to it.categoryLabel, "summary" to it.summary, "content" to it.content, "coverMediaId" to it.coverMediaId, "blocks" to blocks(it.contentBlocks, it.content), "priority" to it.priority, "pinned" to it.pinned, "status" to it.status.name) }
        "WIKI" -> wikiRepository.findById(id).orElseThrow { ResourceNotFoundException("Wiki 条目不存在") }.let { mapOf("slug" to it.slug, "title" to it.title, "summary" to it.summary, "groupName" to it.groupName, "content" to it.content, "icon" to it.icon, "iconMediaId" to it.iconMediaId, "blocks" to blocks(it.contentBlocks, it.content), "linkUrl" to it.linkUrl, "sortOrder" to it.sortOrder, "published" to it.published) }
        else -> historyRepository.findById(id).orElseThrow { ResourceNotFoundException("历史活动不存在") }.let { mapOf("slug" to it.slug, "title" to it.title, "meta" to it.meta, "imageUrl" to it.imageUrl, "coverMediaId" to it.coverMediaId, "altText" to it.altText, "featured" to it.featured, "content" to it.content, "blocks" to blocks(it.contentBlocks, it.content), "happenedAt" to it.happenedAt?.toString(), "published" to it.published) }
    }

    private fun blocks(json: String, fallback: String = ""): JsonNode {
        val node = runCatching { objectMapper.readTree(json) }.getOrNull()
        if (node != null && node.isArray && node.size() > 0) return node
        return objectMapper.readTree(objectMapper.writeValueAsString(listOf(mapOf("type" to "paragraph", "text" to fallback))))
    }

    private fun validatePayload(type: String, payload: JsonNode, requireComplete: Boolean = true) {
        if (!payload.isObject) throw ParamErrorException("草稿内容必须是对象")
        validateFields(payload, payloadFields.getValue(type), "草稿内容")
        validateText(payload, "slug", if (type == "SERVER") 50 else 80, required = requireComplete)
        val slug = payload.path("slug").asString("").trim()
        if (slug.isNotBlank() && !SLUG_REGEX.matches(slug)) throw ParamErrorException("唯一标识只能使用小写字母、数字和连字符")
        if (requireComplete) {
            when (type) {
                "SERVER" -> {
                    validateText(payload, "name", 100, required = true)
                    validateText(payload, "gameplay", 160, required = true)
                    validateText(payload, "address", 255, required = true)
                    validateText(payload, "version", 80, required = true)
                }
                "ACTIVITY" -> {
                    validateText(payload, "name", 140, required = true)
                    validateText(payload, "serverSlug", 50, required = true)
                    validateText(payload, "timeText", 255, required = true)
                    validateText(payload, "participation", 255, required = true)
                    validateText(payload, "description", MAX_TEXT_LENGTH, required = true)
                    val serverSlug = payload.path("serverSlug").asString("").trim()
                    if (serverRepository.findBySlug(serverSlug) == null) throw ParamErrorException("关联服务器不存在")
                }
                "ANNOUNCEMENT" -> {
                    validateText(payload, "title", 180, required = true)
                    validateText(payload, "summary", 500, required = true)
                }
                "WIKI" -> {
                    validateText(payload, "title", 140, required = true)
                    validateText(payload, "summary", 255, required = true)
                }
                "HISTORY" -> {
                    validateText(payload, "title", 160, required = true)
                    validateText(payload, "meta", 255, required = true)
                    validateText(payload, "altText", 255, required = true)
                }
            }
        }
        validateCommonFields(type, payload)
        validateOptionalMediaId(payload, "iconMediaId")
        validateOptionalMediaId(payload, "coverMediaId")
        validateUrl(payload.path("imageUrl").asString(""))
        val blocks = payload.path("blocks")
        if (!blocks.isMissingNode && !blocks.isArray) throw ParamErrorException("正文块格式错误")
        if (blocks.isArray && blocks.size() > MAX_BLOCKS) throw ParamErrorException("正文块数量超过限制")
        if (blocks.isArray) blocks.forEach { block ->
            if (!block.isObject) throw ParamErrorException("正文块格式错误")
            val blockType = block.path("type").asString("")
            if (blockType !in allowedBlockTypes) throw ParamErrorException("正文包含不支持的内容块")
            validateBlockFields(block, blockType)
            validateText(block, "id", 120)
            validateText(block, "text", MAX_TEXT_LENGTH)
            validateText(block, "label", 200)
            validateText(block, "url", 500)
            validateText(block, "src", 500)
            validateText(block, "alt", 255)
            validateText(block, "caption", 500)
            validateInlineContent(block)
            val alignment = block.path("align").asString("").trim()
            if (alignment.isNotBlank() && alignment !in allowedTextAlignments) throw ParamErrorException("正文对齐方式无效")
            if (blockType == "heading" && !isSupportedHeadingLevel(block.path("level"))) throw ParamErrorException("标题层级只支持二级或三级")
            validateOptionalMediaId(block, "mediaId")
            if (blockType == "bulletList" || blockType == "stepList") {
                val items = block.path("items")
                if (!items.isMissingNode && !items.isArray) throw ParamErrorException("列表内容格式错误")
                if (items.isArray && items.size() > MAX_LIST_ITEMS) throw ParamErrorException("单个列表项目过多")
                if (items.isArray) items.forEach { item ->
                    if (item.isString) {
                        if (item.asString("").length > 1000) throw ParamErrorException("列表项目过长")
                    } else if (item.isObject) {
                        validateFields(item, listItemFields, "列表项目")
                        validateText(item, "text", 1000)
                        validateInlineContent(item)
                        if (item.path("text").asString("").isBlank() && item.path("content").isMissingNode) {
                            throw ParamErrorException("列表项目不能为空")
                        }
                    } else {
                        throw ParamErrorException("列表项目格式错误")
                    }
                }
            }
            if (blockType == "gallery") {
                val items = block.path("items")
                if (!items.isMissingNode && !items.isArray) throw ParamErrorException("图片画廊格式错误")
                if (items.isArray && items.size() > 24) throw ParamErrorException("单个图片画廊最多包含 24 张图片")
                if (items.isArray) items.forEach { item ->
                    if (!item.isObject) throw ParamErrorException("图片画廊格式错误")
                    validateFields(item, galleryItemFields, "图片画廊")
                    validateOptionalMediaId(item, "mediaId")
                    validateText(item, "src", 500)
                    validateText(item, "alt", 255)
                    validateText(item, "caption", 500)
                    validateUrl(item.path("src").asString(""))
                }
            }
            if (blockType == "callout") {
                val tone = block.path("tone").asString("").trim()
                if (tone.isNotBlank() && tone !in allowedCalloutTones) throw ParamErrorException("引用样式无效")
            }
            if (requireComplete && blockType == "image" && block.path("src").asString("").isNotBlank() && block.path("alt").asString("").isBlank()) {
                throw ParamErrorException("正文图片必须填写替代文字")
            }
            if (blockType == "link" || blockType == "action") validateUrl(block.path("url").asString(""))
            if (blockType == "image") validateUrl(block.path("src").asString(""))
        }
        validateUrl(payload.path("linkUrl").asString(""))
    }

    private fun validateBlockFields(block: JsonNode, type: String) {
        val allowed = when (type) {
            "heading" -> headingBlockFields
            "paragraph" -> paragraphBlockFields
            "bulletList", "stepList" -> listBlockFields
            "image" -> imageBlockFields
            "gallery" -> galleryBlockFields
            "callout" -> calloutBlockFields
            "code" -> codeBlockFields
            "link", "action" -> linkBlockFields
            else -> emptySet()
        }
        validateFields(block, allowed, "正文块")
    }

    private fun validateFields(node: JsonNode, allowed: Set<String>, label: String) {
        node.propertyNames().forEach { field ->
            if (field !in allowed) throw ParamErrorException("$label 包含不支持的字段")
        }
    }

    private fun isSupportedHeadingLevel(node: JsonNode): Boolean =
        (node.isIntegralNumber && node.asInt() in 2..3) ||
            (node.isString && node.asString() in setOf("2", "3"))

    private fun validateOptionalMediaId(node: JsonNode, key: String) {
        val value = node.path(key)
        if (value.isMissingNode || value.isNull) return
        if (!value.isIntegralNumber || value.asLong() <= 0) throw ParamErrorException("媒体编号格式错误")
    }

    private fun validateInlineContent(block: JsonNode) {
        val content = block.path("content")
        if (content.isMissingNode || content.isNull) return
        if (!content.isArray) throw ParamErrorException("正文富文本格式错误")
        if (content.size() > MAX_INLINE_NODES) throw ParamErrorException("单个正文段落格式节点过多")
        var textLength = 0
        content.forEach { node ->
            if (!node.isObject) throw ParamErrorException("正文富文本格式错误")
            validateFields(node, inlineNodeFields, "正文行内内容")
            val nodeType = node.path("type").asString("")
            if (nodeType !in allowedInlineNodeTypes) throw ParamErrorException("正文包含不支持的行内内容")
            if (nodeType == "text") {
                val textNode = node.path("text")
                if (!textNode.isString) throw ParamErrorException("正文文字格式错误")
                textLength += textNode.asString("").length
                if (textLength > MAX_TEXT_LENGTH) throw ParamErrorException("单个正文段落过长")
            }
            val marks = node.path("marks")
            if (!marks.isMissingNode && !marks.isArray) throw ParamErrorException("正文文字格式错误")
            if (marks.isArray && marks.size() > MAX_INLINE_MARKS) throw ParamErrorException("正文文字格式过多")
            if (marks.isArray) marks.forEach { mark ->
                if (!mark.isObject) throw ParamErrorException("正文文字格式错误")
                validateFields(mark, inlineMarkFields, "正文文字格式")
                val markType = mark.path("type").asString("")
                if (markType !in allowedInlineMarkTypes) throw ParamErrorException("正文包含不支持的文字格式")
                val attrs = mark.path("attrs")
                if (markType == "link") {
                    if (!attrs.isObject) throw ParamErrorException("链接格式错误")
                    validateFields(attrs, linkMarkAttributeFields, "链接")
                    val href = attrs.path("href")
                    if (!href.isString || href.asString().isBlank()) throw ParamErrorException("链接地址不能为空")
                    validateUrl(href.asString())
                    val target = attrs.path("target")
                    if (!target.isMissingNode && !target.isNull && (!target.isString || target.asString() != "_blank")) {
                        throw ParamErrorException("链接打开方式无效")
                    }
                } else if (!attrs.isMissingNode && !attrs.isNull) {
                    throw ParamErrorException("正文文字格式不支持属性")
                }
            }
        }
    }

    private fun validateCommonFields(type: String, payload: JsonNode) {
        when (type) {
            "SERVER" -> {
                validateText(payload, "name", 100)
                validateText(payload, "gameplay", 160)
                validateText(payload, "address", 255)
                validateText(payload, "version", 80)
                validateText(payload, "pack", 255)
                validateText(payload, "description", MAX_TEXT_LENGTH)
                validateText(payload, "rules", MAX_TEXT_LENGTH)
                validateText(payload, "icon", 60)
                validateEnum(payload, "category", ServerCategory.entries.map { it.name }.toSet())
            }
            "ACTIVITY" -> {
                validateText(payload, "name", 140)
                validateText(payload, "statusLabel", 60)
                validateText(payload, "serverSlug", 50)
                validateText(payload, "timeText", 255)
                validateText(payload, "participation", 255)
                validateText(payload, "description", MAX_TEXT_LENGTH)
                validateText(payload, "icon", 60)
                validateEnum(payload, "kind", ActivityKind.entries.map { it.name }.toSet())
                validateEnum(payload, "status", ActivityStatus.entries.map { it.name }.toSet())
            }
            "ANNOUNCEMENT" -> {
                validateText(payload, "title", 180)
                validateText(payload, "category", 30)
                validateText(payload, "categoryLabel", 80)
                validateText(payload, "summary", 500)
                validateText(payload, "content", MAX_TEXT_LENGTH)
            }
            "WIKI" -> {
                validateText(payload, "title", 140)
                validateText(payload, "summary", 255)
                validateText(payload, "groupName", 80)
                validateText(payload, "content", MAX_TEXT_LENGTH)
                validateText(payload, "icon", 60)
                validateText(payload, "linkUrl", 500)
            }
            "HISTORY" -> {
                validateText(payload, "title", 160)
                validateText(payload, "meta", 255)
                validateText(payload, "imageUrl", 500)
                validateText(payload, "altText", 255)
                validateText(payload, "content", MAX_TEXT_LENGTH)
                val happenedAt = payload.path("happenedAt").asString("").trim()
                if (happenedAt.isNotBlank() && runCatching { Instant.parse(happenedAt) }.isFailure) {
                    throw ParamErrorException("发生时间必须是 ISO 时间")
                }
            }
        }
    }

    private fun validateText(node: JsonNode, key: String, maxLength: Int, required: Boolean = false) {
        val valueNode = node.path(key)
        if (valueNode.isMissingNode || valueNode.isNull) {
            if (required) throw ParamErrorException("缺少必填字段：$key")
            return
        }
        if (!valueNode.isString) throw ParamErrorException("字段格式错误：$key")
        val value = valueNode.asString("").trim()
        if (required && value.isBlank()) throw ParamErrorException("缺少必填字段：$key")
        if (value.length > maxLength) throw ParamErrorException("字段过长：$key")
    }

    private fun validateEnum(node: JsonNode, key: String, allowed: Set<String>) {
        val value = node.path(key).asString("").trim()
        if (value.isNotBlank() && value.uppercase() !in allowed) throw ParamErrorException("字段取值无效：$key")
    }

    private fun referencedMediaIds(payload: JsonNode): Set<Long> {
        val ids = mutableSetOf<Long>()
        fun add(node: JsonNode, key: String) {
            val value = node.path(key)
            if (!value.isMissingNode && !value.isNull && value.canConvertToLong()) ids += value.asLong()
        }
        add(payload, "iconMediaId")
        add(payload, "coverMediaId")
        val blocks = payload.path("blocks")
        if (blocks.isArray) blocks.forEach { block ->
            add(block, "mediaId")
            val items = block.path("items")
            if (items.isArray) items.forEach { item -> add(item, "mediaId") }
        }
        return ids
    }

    private fun validateReferencedMedia(
        type: String,
        draftId: String,
        resourceId: Long?,
        payload: JsonNode,
    ): Map<Long, MediaAsset> {
        val ids = referencedMediaIds(payload)
        val mediaById = if (ids.isEmpty()) emptyMap() else mediaRepository.findAllById(ids)
            .associateBy { it.id.requirePresent("MediaAsset.id") }
        val missing = ids - mediaById.keys
        if (missing.isNotEmpty()) throw ParamErrorException("引用的图片不存在或已被清理")

        mediaById.values.forEach { media ->
            val belongsToDraft = media.draftId == draftId && media.resourceType == type
            val belongsToResource = resourceId != null && media.draftId == null &&
                media.resourceType == type && media.resourceId == resourceId
            if (!belongsToDraft && !belongsToResource) {
                throw ParamErrorException("图片只能用于其所属的内容")
            }
            if (media.deletedAt != null && !belongsToResource) {
                throw ParamErrorException("引用的图片已被删除")
            }
        }

        validatePurpose(payload, "iconMediaId", mediaById, setOf("ICON"), "自定义图标")
        validatePurpose(payload, "coverMediaId", mediaById, setOf("COVER"), "封面")
        val blocks = payload.path("blocks")
        if (blocks.isArray) blocks.forEach { block ->
            when (block.path("type").asString("")) {
                "image" -> validateBlockImage(block, mediaById, setOf("CONTENT"))
                "gallery" -> {
                    val items = block.path("items")
                    if (!items.isArray || items.size() == 0) throw ParamErrorException("图片画廊不能为空")
                    if (items.size() > 24) throw ParamErrorException("单个图片画廊最多包含 24 张图片")
                    items.forEach { validateBlockImage(it, mediaById, setOf("CONTENT", "GALLERY")) }
                }
            }
        }
        return mediaById
    }

    private fun validatePurpose(
        node: JsonNode,
        key: String,
        mediaById: Map<Long, MediaAsset>,
        allowed: Set<String>,
        label: String,
    ) {
        val id = mediaId(node, key) ?: return
        if (mediaById.getValue(id).purpose !in allowed) throw ParamErrorException("$label 图片用途不匹配")
    }

    private fun validateBlockImage(node: JsonNode, mediaById: Map<Long, MediaAsset>, allowedPurposes: Set<String>) {
        val id = mediaId(node, "mediaId") ?: throw ParamErrorException("正文图片缺少媒体编号")
        val media = mediaById[id] ?: throw ParamErrorException("正文图片不存在")
        if (media.purpose !in allowedPurposes) throw ParamErrorException("正文图片用途不匹配")
        if (node.path("alt").asString("").trim().isBlank()) throw ParamErrorException("正文图片必须填写替代文字")
        val source = node.path("src").asString("").trim()
        if (source != mediaUrl(media)) throw ParamErrorException("正文图片地址与媒体编号不匹配")
    }

    private fun mediaId(node: JsonNode, key: String): Long? {
        val value = node.path(key)
        return value.takeUnless { it.isMissingNode || it.isNull }?.asLong()
    }

    private fun mediaUrl(media: MediaAsset): String =
        "${fileProperties.baseUrl.trimEnd('/')}/api/public/media/${media.storageName}"

    private fun validateUrl(value: String) {
        val url = value.trim()
        if (url.isBlank()) return
        if (url.any { it.isISOControl() } || '\\' in url) throw ParamErrorException("链接包含不允许的字符")
        if (url.startsWith("/")) {
            if (!url.startsWith("//")) return
            throw ParamErrorException("只允许使用 http、https 或站内路径链接")
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw ParamErrorException("只允许使用 http、https 或站内路径链接")
        }
        val parsed = runCatching { java.net.URI(url) }.getOrElse {
            throw ParamErrorException("链接格式错误")
        }
        if (parsed.host.isNullOrBlank()) throw ParamErrorException("链接格式错误")
    }

    private fun ensureSlugAvailable(type: String, resourceId: Long?, slug: String) {
        val existingId = when (type) {
            "SERVER" -> serverRepository.findBySlug(slug)?.id
            "ACTIVITY" -> activityRepository.findBySlug(slug)?.id
            "ANNOUNCEMENT" -> announcementRepository.findBySlug(slug)?.id
            "WIKI" -> wikiRepository.findBySlug(slug)?.id
            else -> historyRepository.findBySlug(slug)?.id
        }
        if (existingId != null && existingId != resourceId) throw ConflictException("唯一标识已被其他内容使用")
    }

    @Transactional
    private fun applyPayload(type: String, id: Long?, node: JsonNode): Long = when (type) {
        "SERVER" -> saveServer(id, node)
        "ACTIVITY" -> saveActivity(id, node)
        "ANNOUNCEMENT" -> saveAnnouncement(id, node)
        "WIKI" -> saveWiki(id, node)
        else -> saveHistory(id, node)
    }

    private fun saveServer(id: Long?, n: JsonNode): Long {
        val item = id?.let { serverRepository.findById(it).orElseThrow { ResourceNotFoundException("服务器不存在") } } ?: Server()
        item.slug = text(n, "slug"); item.name = text(n, "name"); item.gameplay = text(n, "gameplay"); item.category = enum(n, "category", ServerCategory.PERMANENT); item.address = text(n, "address"); item.version = text(n, "version"); item.pack = text(n, "pack"); item.description = text(n, "description"); item.rules = text(n, "rules"); item.icon = text(n, "icon", "server"); item.iconMediaId = longOrNull(n, "iconMediaId"); item.featured = bool(n, "featured"); item.published = true; item.updatedAt = Instant.now()
        if (id == null) {
            item.status = ServerStatus.OFFLINE
            item.statusLabel = "待检测"
            item.maintenance = false
        }
        return serverRepository.save(item).id.requirePresent("Server.id")
    }

    private fun saveActivity(id: Long?, n: JsonNode): Long {
        val item = id?.let { activityRepository.findById(it).orElseThrow { ResourceNotFoundException("活动不存在") } } ?: Activity()
        item.slug = text(n, "slug"); item.name = text(n, "name"); item.kind = enum(n, "kind", ActivityKind.LONG_TERM); item.status = enum(n, "status", ActivityStatus.UPCOMING); item.statusLabel = text(n, "statusLabel"); item.serverSlug = text(n, "serverSlug"); item.timeText = text(n, "timeText"); item.participation = text(n, "participation"); item.description = text(n, "description"); item.icon = text(n, "icon", "event"); item.iconMediaId = longOrNull(n, "iconMediaId"); item.coverMediaId = longOrNull(n, "coverMediaId"); item.contentBlocks = blocksJson(n); item.requiresPack = bool(n, "requiresPack"); item.priority = int(n, "priority"); item.published = true; item.updatedAt = Instant.now()
        return activityRepository.save(item).id.requirePresent("Activity.id")
    }

    private fun saveAnnouncement(id: Long?, n: JsonNode): Long {
        val item = id?.let { announcementRepository.findById(it).orElseThrow { ResourceNotFoundException("公告不存在") } } ?: Announcement()
        item.slug = text(n, "slug"); item.title = text(n, "title"); item.category = text(n, "category", "club"); item.categoryLabel = text(n, "categoryLabel", "社团通知"); item.summary = text(n, "summary"); item.contentBlocks = blocksJson(n); item.content = plainText(n.path("blocks"), text(n, "content")); item.coverMediaId = longOrNull(n, "coverMediaId"); item.priority = int(n, "priority"); item.pinned = bool(n, "pinned"); item.status = AnnouncementStatus.PUBLISHED; if (item.publishedAt == null) item.publishedAt = Instant.now(); item.updatedAt = Instant.now()
        return announcementRepository.save(item).id.requirePresent("Announcement.id")
    }

    private fun saveWiki(id: Long?, n: JsonNode): Long {
        val item = id?.let { wikiRepository.findById(it).orElseThrow { ResourceNotFoundException("Wiki 条目不存在") } } ?: WikiSection()
        item.slug = text(n, "slug"); item.title = text(n, "title"); item.summary = text(n, "summary"); item.groupName = text(n, "groupName", "入服指南"); item.contentBlocks = blocksJson(n); item.content = plainText(n.path("blocks"), text(n, "content")); item.icon = text(n, "icon", "guide"); item.iconMediaId = longOrNull(n, "iconMediaId"); item.linkUrl = n.path("linkUrl").asString("").trim().ifBlank { null }; item.sortOrder = int(n, "sortOrder"); item.published = true; item.updatedAt = Instant.now()
        return wikiRepository.save(item).id.requirePresent("WikiSection.id")
    }

    private fun saveHistory(id: Long?, n: JsonNode): Long {
        val item = id?.let { historyRepository.findById(it).orElseThrow { ResourceNotFoundException("历史活动不存在") } } ?: HistoryItem()
        item.slug = text(n, "slug"); item.title = text(n, "title"); item.meta = text(n, "meta"); item.imageUrl = n.path("imageUrl").asString("").trim().ifBlank { null }; item.coverMediaId = longOrNull(n, "coverMediaId"); item.altText = text(n, "altText", "活动记录"); item.featured = bool(n, "featured"); item.contentBlocks = blocksJson(n); item.content = plainText(n.path("blocks"), text(n, "content")); item.happenedAt = n.path("happenedAt").asString("").takeIf { it.isNotBlank() }?.let(Instant::parse); item.published = true; item.updatedAt = Instant.now()
        return historyRepository.save(item).id.requirePresent("HistoryItem.id")
    }

    private fun blocksJson(n: JsonNode): String = if (n.path("blocks").isArray) objectMapper.writeValueAsString(n.path("blocks")) else "[]"
    private fun plainText(blocks: JsonNode, fallback: String): String = if (!blocks.isArray) fallback else blocks.mapNotNull { it.path("text").asString("").takeIf(String::isNotBlank) }.joinToString("\n\n").ifBlank { fallback }
    private fun text(n: JsonNode, key: String, fallback: String = ""): String = n.path(key).asString(fallback).trim()
    private fun int(n: JsonNode, key: String): Int = n.path(key).asInt(0)
    private fun bool(n: JsonNode, key: String): Boolean = n.path(key).asBoolean(false)
    private fun longOrNull(n: JsonNode, key: String): Long? = n.path(key).takeUnless { it.isMissingNode || it.isNull }?.asLong()
    private inline fun <reified T : Enum<T>> enum(n: JsonNode, key: String, fallback: T): T = runCatching { enumValueOf<T>(text(n, key).uppercase()) }.getOrDefault(fallback)

    private fun audit(operatorId: Long?, type: String, id: String, action: String) {
        operatorId?.let { auditLogRepository.save(AuditLog(operatorUserId = it, resourceType = type, resourceId = id, action = action)) }
    }

    private companion object {
        const val MAX_DRAFT_BYTES = 1_000_000
        const val MAX_BLOCKS = 200
        const val MAX_LIST_ITEMS = 100
        const val MAX_INLINE_NODES = 2_000
        const val MAX_INLINE_MARKS = 8
        const val MAX_TEXT_LENGTH = 50_000
        val SLUG_REGEX = Regex("[a-z0-9][a-z0-9-]*")
        val payloadFields = mapOf(
            "SERVER" to setOf("slug", "name", "gameplay", "category", "address", "version", "pack", "description", "rules", "icon", "iconMediaId", "featured", "published"),
            "ACTIVITY" to setOf("slug", "name", "kind", "status", "statusLabel", "serverSlug", "timeText", "participation", "description", "icon", "iconMediaId", "coverMediaId", "blocks", "requiresPack", "priority", "published"),
            "ANNOUNCEMENT" to setOf("slug", "title", "category", "categoryLabel", "summary", "content", "coverMediaId", "blocks", "priority", "pinned", "status"),
            "WIKI" to setOf("slug", "title", "summary", "groupName", "content", "icon", "iconMediaId", "blocks", "linkUrl", "sortOrder", "published"),
            "HISTORY" to setOf("slug", "title", "meta", "imageUrl", "coverMediaId", "altText", "featured", "content", "blocks", "happenedAt", "published"),
        )
        val headingBlockFields = setOf("id", "type", "level", "text", "content", "align")
        val paragraphBlockFields = setOf("id", "type", "text", "content", "align")
        val listBlockFields = setOf("id", "type", "items", "itemsText")
        val imageBlockFields = setOf("id", "type", "mediaId", "src", "alt", "caption")
        val galleryBlockFields = setOf("id", "type", "items")
        val calloutBlockFields = setOf("id", "type", "text", "content", "tone")
        val codeBlockFields = setOf("id", "type", "text")
        val linkBlockFields = setOf("id", "type", "label", "url")
        val galleryItemFields = setOf("mediaId", "src", "alt", "caption")
        val listItemFields = setOf("text", "content")
        val inlineNodeFields = setOf("type", "text", "marks")
        val inlineMarkFields = setOf("type", "attrs")
        val linkMarkAttributeFields = setOf("href", "target")
    }
}
