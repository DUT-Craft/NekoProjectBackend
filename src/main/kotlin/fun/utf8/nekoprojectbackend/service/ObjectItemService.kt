package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.*
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

data class NeedMemberItemRequest(
    val skill: String = "",
    val number: Long? = null,
    val context: String? = null,
)

data class NeedMemberItemResponse(
    val skill: String?,
    val number: Long?,
    val context: String?,
)

data class ObjectItemSaveRequest(
    val title: String = "",
    val type: String = "",
    val introduction: String? = null,
    val description: String? = null,
    // 未指定时为 null：公开投稿由 toEntity() 固化为 PENDING；管理员创建时由 controller 按角色兜底
    // （总管理默认 RECRUITING、项目管理强制 PENDING）。
    var status: ObjectItemStatus? = null,
    val leader: String? = null,
    val needMembers: List<NeedMemberItemRequest>? = null,
    val tags: List<String>? = null,
    val leaderMcId: String? = null,
    val contactInformation: String? = null,
    val coverImageUrl: String? = null,
    val controlPassword: String? = null,
)

data class ObjectItemBatchSaveRequest(
    val items: List<ObjectItemSaveRequest> = emptyList(),
)

data class ObjectItemUpdateRequest(
    val id: Int? = null,
    val title: String? = null,
    val type: String? = null,
    val introduction: String? = null,
    val description: String? = null,
    val status: ObjectItemStatus? = null,
    val leader: String? = null,
    val needMembers: List<NeedMemberItemRequest>? = null,
    val tags: List<String>? = null,
    val leaderMcId: String? = null,
    val contactInformation: String? = null,
    val coverImageUrl: String? = null,
    val controlPassword: String? = null,
)

data class ObjectItemBatchUpdateRequest(
    val items: List<ObjectItemUpdateRequest> = emptyList(),
)

data class ObjectItemBatchDeleteRequest(
    val ids: List<Int> = emptyList(),
)

data class ObjectItemQueryRequest(
    val ids: List<Int>? = null,
    val title: String? = null,
    val type: String? = null,
    val status: ObjectItemStatus? = null,
    val statuses: List<ObjectItemStatus>? = null,
    val leader: String? = null,
    val leaderMcId: String? = null,
    val tags: List<String>? = null,
    val ownerId: Long? = null,
)

data class ObjectItemResponse(
    val id: Int?,
    val title: String?,
    val type: String?,
    val introduction: String?,
    val description: String?,
    val status: ObjectItemStatus?,
    val leader: String?,
    val needMembers: List<NeedMemberItemResponse>,
    val tags: List<String>,
    val leaderMcId: String?,
    val contactInformation: String?,
    val coverImageUrl: String?,
    val ownerId: Long?,
    val hasControlPassword: Boolean,
)

data class ObjectItemPageVO(
    val content: List<ObjectItemResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

enum class ObjectItemSortProperty(val alias: String) {
    ID("id"),
    ;

    companion object {
        fun from(value: String): ObjectItemSortProperty? =
            entries.firstOrNull { it.alias.equals(value, ignoreCase = true) }
    }
}

enum class SortDirection {
    ASC,
    DESC,
    ;

    companion object {
        fun from(value: String): SortDirection? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** 项目条目业务：增删改查、批量操作、分页/排序、多条件过滤与字段长度校验。 */
@Service
class ObjectItemService(
    private val objectItemRepository: ObjectItemRepository,
    private val userRepository: UserRepository,
    @Value("\${neko.project.max-per-manager:10}") private val maxPerManager: Long,
) {

    @Transactional
    fun save(request: ObjectItemSaveRequest): ObjectItemResponse {
        // 注意：toEntity() 会把 status 固化为 PENDING，此处的 RECRUITING 赋值实际不生效（疑似遗留，建议确认）。
        request.status = ObjectItemStatus.RECRUITING
        val entity = request.toEntity()
        return objectItemRepository.save(entity).toResponse()
    }

    @Transactional
    fun saveBatch(requests: List<ObjectItemSaveRequest>): List<ObjectItemResponse> {
        validateBatchSize(requests, "批量保存项目条目不能为空")
        return objectItemRepository.saveAll(requests.map { it.toEntity() })
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findById(id: Int): ObjectItemResponse {
        val item = findObjectItem(id)
        return item.toResponse()
    }

    @Transactional(readOnly = true)
    fun query(request: ObjectItemQueryRequest): List<ObjectItemResponse> {
        return filterObjectItems(request)
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional(readOnly = true)
    fun queryPage(request: ObjectItemQueryRequest, page: Int, size: Int, sort: String): ObjectItemPageVO {
        if (page < 0) {
            throw ParamErrorException("页码不能小于 0")
        }
        if (size <= 0) {
            throw ParamErrorException("每页条数必须大于 0")
        }
        if (size > MAX_PAGE_SIZE) {
            throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")
        }
        val (property, direction) = parseSort(sort)

        val sorted = filterObjectItems(request).let { all ->
            val comparator: Comparator<ObjectItem> = when (property) {
                ObjectItemSortProperty.ID -> compareBy { it.id ?: Int.MAX_VALUE }
            }
            if (direction == SortDirection.DESC) all.sortedWith(comparator.reversed()) else all.sortedWith(comparator)
        }

        val total = sorted.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        val fromIndex = minOf(page * size, sorted.size)
        val toIndex = minOf(fromIndex + size, sorted.size)
        val pageContent = sorted.subList(fromIndex, toIndex).map { it.toResponse() }

        return ObjectItemPageVO(
            content = pageContent,
            totalElements = total,
            totalPages = totalPages,
            page = page,
            size = size,
        )
    }

    private fun filterObjectItems(request: ObjectItemQueryRequest): List<ObjectItem> {
        val ids = normalizeIds(request.ids)
        val normalizedTitle = normalizeNullableText(request.title)
        val normalizedType = normalizeNullableText(request.type)
        val normalizedLeader = normalizeNullableText(request.leader)
        val normalizedLeaderMcId = normalizeNullableText(request.leaderMcId)
        val normalizedTags = cleanTags(request.tags)
        val requestedStatuses = normalizeStatuses(request.status, request.statuses)
        val filterByStatus = requestedStatuses.isNotEmpty()

        val source = if (ids.isNullOrEmpty()) {
            objectItemRepository.findAll()
        } else {
            objectItemRepository.findAllById(ids).toList()
        }

        return source.asSequence()
            .filter { normalizedTitle == null || it.title?.contains(normalizedTitle, ignoreCase = true) == true }
            .filter { normalizedType == null || it.type?.equals(normalizedType, ignoreCase = true) == true }
            .filter { !filterByStatus || it.status in requestedStatuses }
            .filter { normalizedLeader == null || it.leader?.contains(normalizedLeader, ignoreCase = true) == true }
            .filter {
                normalizedLeaderMcId == null || it.leaderMcId?.equals(
                    normalizedLeaderMcId,
                    ignoreCase = true
                ) == true
            }
            .filter { normalizedTags.isEmpty() || it.containsAllTags(normalizedTags) }
            .filter { request.ownerId == null || it.ownerId == request.ownerId }
            .toList()
    }

    private fun normalizeStatuses(status: ObjectItemStatus?, statuses: List<ObjectItemStatus>?): Set<ObjectItemStatus> {
        return (listOfNotNull(status) + statuses.orEmpty()).toSet()
    }

    private fun parseSort(sort: String): Pair<ObjectItemSortProperty, SortDirection> {
        val parts = sort.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return ObjectItemSortProperty.ID to SortDirection.DESC
        }
        val property = ObjectItemSortProperty.from(parts[0])
            ?: throw ParamErrorException("不支持的排序字段：${parts[0]}，支持 id")
        val direction = if (parts.size > 1) {
            SortDirection.from(parts[1])
                ?: throw ParamErrorException("不支持的排序方向：${parts[1]}，支持 asc / desc")
        } else {
            SortDirection.DESC
        }
        return property to direction
    }

    @Transactional(readOnly = true)
    fun findByStatus(status: ObjectItemStatus): List<ObjectItemResponse> {
        return objectItemRepository.findByStatus(status)
            .asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional(readOnly = true)
    fun countInProgress(): Long {
        return objectItemRepository.countByStatus(ObjectItemStatus.IN_PROGRESS)
    }

    @Transactional
    fun update(id: Int, request: ObjectItemUpdateRequest): ObjectItemResponse {
        val objectItem = findObjectItem(id)
        objectItem.applyUpdate(request)
        return objectItemRepository.save(objectItem).toResponse()
    }

    @Transactional
    fun updateBatch(requests: List<ObjectItemUpdateRequest>): List<ObjectItemResponse> {
        validateBatchSize(requests, "批量更新项目条目不能为空")

        val ids = requests.map {
            val id = it.id ?: throw ParamErrorException("批量更新时项目条目 ID 不能为空")
            requirePositiveId(id)
        }
        val duplicateIds = ids.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw ParamErrorException("批量更新不能包含重复项目条目 ID：${duplicateIds.joinToString(", ")}")
        }

        val objectItemsById = objectItemRepository.findAllById(ids)
            .associateBy { it.id }
        val missingIds = ids.filter { objectItemsById[it] == null }
        if (missingIds.isNotEmpty()) {
            throw ResourceNotFoundException("项目条目不存在：${missingIds.joinToString(", ")}")
        }

        val objectItems = requests.map { request ->
            val objectItem = objectItemsById.getValue(request.id)
            objectItem.applyUpdate(request)
            objectItem
        }

        return objectItemRepository.saveAll(objectItems)
            .map { it.toResponse() }
    }

    @Transactional
    fun deleteBatch(ids: List<Int>) {
        validateBatchSize(ids, "批量删除项目条目不能为空")
        val deleteRequests = ids.map { ObjectItemUpdateRequest(id = it, status = ObjectItemStatus.DELETED) }
        updateBatch(deleteRequests)
    }

    /**
     * 总管理把项目分配给某个账号（ownerId=null 表示收回为未分配）。
     * 项目管理与总管理均可被指定为归属人（总管理也可拥有并管理自有项目），仅校验账号存在与名下上限。
     */
    @Transactional
    fun assignOwner(id: Int, ownerId: Long?): ObjectItemResponse {
        val item = findObjectItem(id)
        if (ownerId != null) {
            userRepository.findById(ownerId)
                .orElseThrow { ResourceNotFoundException("用户不存在") }
            // 重新分配给同一人不重复计入上限
            val alreadyOwned = item.ownerId == ownerId
            if (!alreadyOwned && objectItemRepository.countByOwnerId(ownerId) >= maxPerManager) {
                throw ParamErrorException("该用户名下项目已达上限 $maxPerManager")
            }
        }
        item.ownerId = ownerId
        return objectItemRepository.save(item).toResponse()
    }

    /**
     * 管理员直接创建并归属到自己的项目：总管理可指定状态（绕过审核，默认 RECRUITING 上线），
     * 项目管理强制 PENDING 等待总管理审核。创建即归属，受名下项目上限约束。
     */
    @Transactional
    fun saveOwned(request: ObjectItemSaveRequest, ownerId: Long, status: ObjectItemStatus): ObjectItemResponse {
        if (objectItemRepository.countByOwnerId(ownerId) >= maxPerManager) {
            throw ParamErrorException("名下项目已达上限 $maxPerManager")
        }
        val entity = request.toEntity()
        entity.status = status
        entity.ownerId = ownerId
        return objectItemRepository.save(entity).toResponse()
    }

    private fun ObjectItemSaveRequest.toEntity(): ObjectItem {
        return ObjectItem().also {
            it.title =
                requireText(title, "项目标题不能为空", MAX_TITLE_LENGTH, "项目标题不能超过 $MAX_TITLE_LENGTH 个字符")
            it.type = requireText(type, "项目类型不能为空", MAX_TYPE_LENGTH, "项目类型不能超过 $MAX_TYPE_LENGTH 个字符")
            it.introduction = normalizeNullableText(
                introduction,
                MAX_INTRODUCTION_LENGTH,
                "项目简介不能超过 $MAX_INTRODUCTION_LENGTH 个字符"
            )
            it.description = normalizeNullableText(description)
            it.status = ObjectItemStatus.PENDING
            it.leader = normalizeNullableText(leader, MAX_LEADER_LENGTH, "项目负责人不能超过 $MAX_LEADER_LENGTH 个字符")
            it.needMembers = cleanNeedMembers(needMembers).toMutableList()
            it.tags = cleanTags(tags).toMutableList()
            it.leaderMcId = normalizeNullableText(
                leaderMcId,
                MAX_LEADER_MC_ID_LENGTH,
                "负责人 Minecraft ID 不能超过 $MAX_LEADER_MC_ID_LENGTH 个字符"
            )
            it.contactInformation = normalizeNullableText(
                contactInformation,
                MAX_CONTACT_INFORMATION_LENGTH,
                "联系方式不能超过 $MAX_CONTACT_INFORMATION_LENGTH 个字符",
            )
            it.coverImageUrl = normalizeNullableText(
                coverImageUrl,
                MAX_COVER_IMAGE_URL_LENGTH,
                "封面图地址不能超过 $MAX_COVER_IMAGE_URL_LENGTH 个字符",
            )
            it.controlPassword = normalizeNullableText(
                controlPassword,
                MAX_CONTROL_PASSWORD_LENGTH,
                "项目控制密码不能超过 $MAX_CONTROL_PASSWORD_LENGTH 个字符"
            )
        }
    }

    private fun ObjectItem.applyUpdate(request: ObjectItemUpdateRequest) {
        request.title?.let {
            title = requireText(it, "项目标题不能为空", MAX_TITLE_LENGTH, "项目标题不能超过 $MAX_TITLE_LENGTH 个字符")
        }
        request.type?.let {
            type = requireText(it, "项目类型不能为空", MAX_TYPE_LENGTH, "项目类型不能超过 $MAX_TYPE_LENGTH 个字符")
        }
        request.introduction?.let {
            introduction =
                normalizeNullableText(it, MAX_INTRODUCTION_LENGTH, "项目简介不能超过 $MAX_INTRODUCTION_LENGTH 个字符")
        }
        request.description?.let { description = normalizeNullableText(it) }
        request.status?.let { status = it }
        request.leader?.let {
            leader = normalizeNullableText(it, MAX_LEADER_LENGTH, "项目负责人不能超过 $MAX_LEADER_LENGTH 个字符")
        }
        request.needMembers?.let { needMembers = cleanNeedMembers(it).toMutableList() }
        request.tags?.let { tags = cleanTags(it).toMutableList() }
        request.leaderMcId?.let {
            leaderMcId = normalizeNullableText(
                it,
                MAX_LEADER_MC_ID_LENGTH,
                "负责人 Minecraft ID 不能超过 $MAX_LEADER_MC_ID_LENGTH 个字符"
            )
        }
        request.contactInformation?.let {
            contactInformation = normalizeNullableText(
                it,
                MAX_CONTACT_INFORMATION_LENGTH,
                "联系方式不能超过 $MAX_CONTACT_INFORMATION_LENGTH 个字符"
            )
        }
        request.coverImageUrl?.let {
            coverImageUrl = normalizeNullableText(
                it,
                MAX_COVER_IMAGE_URL_LENGTH,
                "封面图地址不能超过 $MAX_COVER_IMAGE_URL_LENGTH 个字符"
            )
        }
        request.controlPassword?.let {
            controlPassword = normalizeNullableText(
                it,
                MAX_CONTROL_PASSWORD_LENGTH,
                "项目控制密码不能超过 $MAX_CONTROL_PASSWORD_LENGTH 个字符"
            )
        }
    }

    private fun findObjectItem(id: Int): ObjectItem {
        return objectItemRepository.findById(requirePositiveId(id))
            .orElseThrow { ResourceNotFoundException("项目条目不存在") }
    }

    private fun requirePositiveId(id: Int): Int {
        if (id <= 0) {
            throw ParamErrorException("项目条目 ID 必须大于 0")
        }
        return id
    }

    private fun normalizeIds(ids: List<Int>?): List<Int>? {
        if (ids == null) {
            return null
        }
        return ids.map { requirePositiveId(it) }
            .distinct()
            .takeIf { it.isNotEmpty() }
    }

    private fun <T> validateBatchSize(items: List<T>, emptyMessage: String) {
        if (items.isEmpty()) {
            throw ParamErrorException(emptyMessage)
        }
        if (items.size > MAX_BATCH_SIZE) {
            throw ParamErrorException("单次批量操作不能超过 $MAX_BATCH_SIZE 条")
        }
    }

    private fun requireText(
        value: String,
        blankMessage: String,
        maxLength: Int,
        tooLongMessage: String,
    ): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw ParamErrorException(blankMessage)
        }
        if (normalized.length > maxLength) {
            throw ParamErrorException(tooLongMessage)
        }
        return normalized
    }

    private fun normalizeNullableText(value: String?): String? {
        return value?.trim()?.ifBlank { null }
    }

    private fun normalizeNullableText(value: String?, maxLength: Int, tooLongMessage: String): String? {
        val normalized = normalizeNullableText(value)
        if (normalized != null && normalized.length > maxLength) {
            throw ParamErrorException(tooLongMessage)
        }
        return normalized
    }

    private fun cleanNeedMembers(items: List<NeedMemberItemRequest>?): List<NeedMemberItem> {
        val normalizedItems = items.orEmpty()
        if (normalizedItems.size > MAX_NEED_MEMBER_SIZE) {
            throw ParamErrorException("项目招募需求不能超过 $MAX_NEED_MEMBER_SIZE 条")
        }

        return normalizedItems.map { item ->
            val number = item.number
            if (number != null && number < 0) {
                throw ParamErrorException("项目招募人数不能小于 0")
            }

            NeedMemberItem().also {
                it.skill = requireText(
                    item.skill,
                    "项目招募技能不能为空",
                    MAX_NEED_MEMBER_SKILL_LENGTH,
                    "项目招募技能不能超过 $MAX_NEED_MEMBER_SKILL_LENGTH 个字符",
                )
                it.number = number
                it.context = normalizeNullableText(
                    item.context,
                    MAX_NEED_MEMBER_CONTEXT_LENGTH,
                    "项目招募说明不能超过 $MAX_NEED_MEMBER_CONTEXT_LENGTH 个字符",
                )
            }
        }
    }

    private fun cleanTags(tags: List<String>?): List<String> {
        return tags.orEmpty()
            .mapNotNull { normalizeNullableText(it, MAX_TAG_LENGTH, "项目标签不能超过 $MAX_TAG_LENGTH 个字符") }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun ObjectItem.containsAllTags(requiredTags: List<String>): Boolean {
        val existingTags = tags.orEmpty()
        return requiredTags.all { requiredTag ->
            existingTags.any { it.equals(requiredTag, ignoreCase = true) }
        }
    }

    private fun ObjectItem.toResponse(): ObjectItemResponse {
        return ObjectItemResponse(
            id = id,
            title = title,
            type = type,
            introduction = introduction,
            description = description,
            status = status,
            leader = leader,
            needMembers = needMembers.orEmpty().map { it.toResponse() },
            tags = tags.orEmpty().toList(),
            leaderMcId = leaderMcId,
            contactInformation = contactInformation,
            coverImageUrl = coverImageUrl,
            ownerId = ownerId,
            hasControlPassword = !controlPassword.isNullOrBlank(),
        )
    }

    private fun NeedMemberItem.toResponse(): NeedMemberItemResponse {
        return NeedMemberItemResponse(
            skill = skill,
            number = number,
            context = context,
        )
    }

    private companion object {
        private const val MAX_BATCH_SIZE = 100
        private const val MAX_TITLE_LENGTH = 128
        private const val MAX_TYPE_LENGTH = 64
        private const val MAX_INTRODUCTION_LENGTH = 255
        private const val MAX_LEADER_LENGTH = 64
        private const val MAX_NEED_MEMBER_SIZE = 100
        private const val MAX_NEED_MEMBER_SKILL_LENGTH = 64
        private const val MAX_NEED_MEMBER_CONTEXT_LENGTH = 255
        private const val MAX_LEADER_MC_ID_LENGTH = 64
        private const val MAX_CONTACT_INFORMATION_LENGTH = 255
        private const val MAX_COVER_IMAGE_URL_LENGTH = 512
        private const val MAX_CONTROL_PASSWORD_LENGTH = 255
        private const val MAX_TAG_LENGTH = 32
        private const val MAX_PAGE_SIZE = 1024
    }
}
