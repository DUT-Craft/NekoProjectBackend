package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.*
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    val introduction: String? = null,
    val description: String? = null,
    // 未指定时为 null：公开投稿由 toEntity() 固化为 PENDING；管理员创建时由 controller 按角色兜底
    // （总管理默认 RECRUITING、项目管理强制 PENDING）。
    var status: ObjectItemStatus? = null,
    val leader: String? = null,
    val needMembers: List<NeedMemberItemRequest>? = null,
    /** 标签 ID 列表；0~10 个，由 [TagService.resolveSelectableTags] 统一校验（去重 / 存在性 / 可选性）。 */
    val tagIds: List<Long>? = null,
    val leaderMcId: String? = null,
    val contactInformation: String? = null,
    val coverImageUrl: String? = null,
)

data class ObjectItemBatchSaveRequest(
    val items: List<ObjectItemSaveRequest> = emptyList(),
)

data class ObjectItemUpdateRequest(
    val id: Int? = null,
    val title: String? = null,
    val introduction: String? = null,
    val description: String? = null,
    val status: ObjectItemStatus? = null,
    val leader: String? = null,
    val needMembers: List<NeedMemberItemRequest>? = null,
    /** 标签 ID：null=不修改；空数组=清空；非空=完整替换。 */
    val tagIds: List<Long>? = null,
    val leaderMcId: String? = null,
    val contactInformation: String? = null,
    val coverImageUrl: String? = null,
)

data class ObjectItemBatchUpdateRequest(
    val items: List<ObjectItemUpdateRequest> = emptyList(),
)

data class ObjectItemBatchDeleteRequest(
    val ids: List<Int> = emptyList(),
)

/** 多标签匹配方式：ANY=命中任一；ALL=同时命中全部。 */
enum class TagMatch {
    ANY,
    ALL,
    ;

    companion object {
        fun from(value: String?): TagMatch? =
            value?.let { v ->
                entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
                    ?: throw ParamErrorException("不支持的标签匹配方式：$v，支持 ANY / ALL")
            }
    }
}

data class ObjectItemQueryRequest(
    val ids: List<Int>? = null,
    /** 关键字：跨 标题 / 简介 / 描述 / 负责人 / 标签名 模糊匹配。 */
    val keyword: String? = null,
    /** 仅按标题模糊匹配（向后保留）。 */
    val title: String? = null,
    val status: ObjectItemStatus? = null,
    val statuses: List<ObjectItemStatus>? = null,
    val leader: String? = null,
    val leaderMcId: String? = null,
    /** Cascader 选中的标签 ID。 */
    val tagIds: List<Long>? = null,
    val tagMatch: TagMatch? = null,
    val ownerId: Long? = null,
)

data class ObjectItemResponse(
    val id: Int?,
    val title: String?,
    val introduction: String?,
    val description: String?,
    val status: ObjectItemStatus?,
    val leader: String?,
    val needMembers: List<NeedMemberItemResponse>,
    val tags: List<TagSummaryResponse>,
    val leaderMcId: String?,
    val contactInformation: String?,
    val coverImageUrl: String?,
    val ownerId: Long?,
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

/** 项目墙可见状态集合：通过审核（APPROVED）且未删除，含全部运营生命周期状态。 */
val PUBLIC_STATUSES: Set<ObjectItemStatus> = setOf(
    ObjectItemStatus.APPROVED,
    ObjectItemStatus.PREPARING,
    ObjectItemStatus.RECRUITING,
    ObjectItemStatus.IN_PROGRESS,
    ObjectItemStatus.PAUSED,
)

/**
 * 项目条目业务：增删改查、批量操作、数据库侧分页 / 排序与多条件过滤（关键字 + 标签 + 状态）。
 *
 * 标签校验统一委托 [TagService.resolveSelectableTags]：0~10 个、不可重复、必须存在且为可选活跃节点。
 */
@Service
class ObjectItemService(
    private val objectItemRepository: ObjectItemRepository,
    private val userRepository: UserRepository,
    private val tagService: TagService,
    @Value("\${neko.project.max-per-manager:10}") private val maxPerManager: Long,
) {

    @Transactional
    fun save(request: ObjectItemSaveRequest): ObjectItemResponse {
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
        val spec = buildSpecification(request)
        val sorted = Sort.by(Sort.Direction.DESC, ObjectItemSortProperty.ID.alias)
        val items = if (spec == null) {
            objectItemRepository.findAll(sorted)
        } else {
            objectItemRepository.findAll(spec, sorted)
        }
        return items.map { it.toResponse() }
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
        val pageable = PageRequest.of(page, size, toSpringSort(sort))
        val spec = buildSpecification(request)
        val result = if (spec == null) {
            objectItemRepository.findAll(pageable)
        } else {
            objectItemRepository.findAll(spec, pageable)
        }

        return ObjectItemPageVO(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size,
        )
    }

    @Transactional(readOnly = true)
    fun findByStatus(status: ObjectItemStatus): List<ObjectItemResponse> {
        return objectItemRepository.findByStatus(status)
            .map { it.toResponse() }
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
            it.introduction = normalizeNullableText(
                introduction,
                MAX_INTRODUCTION_LENGTH,
                "项目简介不能超过 $MAX_INTRODUCTION_LENGTH 个字符"
            )
            it.description = normalizeNullableText(description)
            it.status = ObjectItemStatus.PENDING
            it.leader = normalizeNullableText(leader, MAX_LEADER_LENGTH, "项目负责人不能超过 $MAX_LEADER_LENGTH 个字符")
            it.needMembers = cleanNeedMembers(needMembers).toMutableList()
            it.tags = tagService.resolveSelectableTags(tagIds ?: emptyList())
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
        }
    }

    private fun ObjectItem.applyUpdate(request: ObjectItemUpdateRequest) {
        request.title?.let {
            title = requireText(it, "项目标题不能为空", MAX_TITLE_LENGTH, "项目标题不能超过 $MAX_TITLE_LENGTH 个字符")
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
        // tagIds：null 不动；空数组清空；非空完整替换（统一校验后原地变更持久化集合）
        request.tagIds?.let { ids ->
            val resolved = tagService.resolveSelectableTags(ids)
            tags.clear()
            tags.addAll(resolved)
        }
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

    /**
     * 把查询请求组装为 JPA Specification；全部条件为空时返回 null（由调用方走无条件查询）。
     * 关键字与标签匹配均使用 EXISTS 子查询，主查询不产生 join，避免笛卡尔积与重复行。
     */
    private fun buildSpecification(request: ObjectItemQueryRequest): Specification<ObjectItem>? {
        val specs = mutableListOf<Specification<ObjectItem>>()

        normalizeNullableText(request.keyword)?.let { kw -> specs += keywordSpec(kw) }
        normalizeNullableText(request.title)?.let { t -> specs += containsSpec("title", t) }
        normalizeNullableText(request.leader)?.let { l -> specs += containsSpec("leader", l) }
        request.leaderMcId?.trim()?.takeIf { it.isNotBlank() }?.let { m -> specs += equalsIgnoreCaseSpec("leaderMcId", m) }
        request.ownerId?.let { oid -> specs += Specification { root, _, cb -> cb.equal(root.get<Long>("ownerId"), oid) } }
        normalizeIds(request.ids)?.let { ids ->
            specs += Specification { root, _, _ -> root.get<Int>("id").`in`(ids) }
        }
        val statuses = normalizeStatuses(request.status, request.statuses)
        if (statuses.isNotEmpty()) {
            specs += Specification { root, _, _ -> root.get<ObjectItemStatus>("status").`in`(statuses) }
        }
        request.tagIds?.filter { it > 0L }?.distinct()?.takeIf { it.isNotEmpty() }?.let { ids ->
            val match = request.tagMatch ?: TagMatch.ANY
            specs += when (match) {
                TagMatch.ANY -> tagAnySpec(ids)
                TagMatch.ALL -> tagAllSpec(ids)
            }
        }

        return if (specs.isEmpty()) null else specs.reduce { acc, s -> acc.and(s) }
    }

    private fun keywordSpec(keyword: String): Specification<ObjectItem> = Specification { root, query, cb ->
        val pattern = "%${keyword.lowercase()}%"
        val tagSub = query.subquery(Long::class.javaObjectType)
        val subProject = tagSub.from(ObjectItem::class.java)
        val tag = subProject.joinSet<ObjectItem, Tag>("tags")
        tagSub.select(tag.get<Long>("id"))
        tagSub.where(
            cb.equal(subProject.get<Int>("id"), root.get<Int>("id")),
            cb.like(cb.lower(tag.get("name")), pattern),
        )
        cb.or(
            cb.like(cb.lower(root.get("title")), pattern),
            cb.like(cb.lower(root.get("introduction")), pattern),
            cb.like(cb.lower(root.get("description")), pattern),
            cb.like(cb.lower(root.get("leader")), pattern),
            cb.exists(tagSub),
        )
    }

    private fun containsSpec(field: String, raw: String): Specification<ObjectItem> = Specification { root, _, cb ->
        cb.like(cb.lower(root.get<String>(field)), "%${raw.lowercase()}%")
    }

    private fun equalsIgnoreCaseSpec(field: String, raw: String): Specification<ObjectItem> = Specification { root, _, cb ->
        cb.equal(cb.lower(root.get<String>(field)), raw.lowercase())
    }

    private fun tagAnySpec(tagIds: List<Long>): Specification<ObjectItem> = Specification { root, query, cb ->
        val sub = query.subquery(Long::class.javaObjectType)
        val subProject = sub.from(ObjectItem::class.java)
        val tag = subProject.joinSet<ObjectItem, Tag>("tags")
        sub.select(tag.get<Long>("id"))
        sub.where(
            cb.equal(subProject.get<Int>("id"), root.get<Int>("id")),
            tag.get<Long>("id").`in`(tagIds),
        )
        cb.exists(sub)
    }

    private fun tagAllSpec(tagIds: List<Long>): Specification<ObjectItem> = Specification { root, query, cb ->
        val sub = query.subquery(Long::class.javaObjectType)
        val subProject = sub.from(ObjectItem::class.java)
        val tag = subProject.joinSet<ObjectItem, Tag>("tags")
        sub.select(cb.countDistinct(tag.get<Long>("id")))
        sub.where(
            cb.equal(subProject.get<Int>("id"), root.get<Int>("id")),
            tag.get<Long>("id").`in`(tagIds),
        )
        cb.equal(sub, tagIds.size.toLong())
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

    private fun toSpringSort(sort: String): Sort {
        val (property, direction) = parseSort(sort)
        val dir = if (direction == SortDirection.DESC) Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(dir, property.alias)
    }

    private fun ObjectItem.toResponse(): ObjectItemResponse {
        return ObjectItemResponse(
            id = id,
            title = title,
            introduction = introduction,
            description = description,
            status = status,
            leader = leader,
            needMembers = needMembers.orEmpty().map { it.toResponse() },
            tags = tags.orEmpty()
                .map { TagSummaryResponse(id = it.id ?: 0L, name = it.name ?: "", parentId = it.parentId) }
                .sortedBy { it.id },
            leaderMcId = leaderMcId,
            contactInformation = contactInformation,
            coverImageUrl = coverImageUrl,
            ownerId = ownerId,
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
        const val MAX_BATCH_SIZE = 100
        const val MAX_TITLE_LENGTH = 128
        const val MAX_INTRODUCTION_LENGTH = 255
        const val MAX_LEADER_LENGTH = 64
        const val MAX_NEED_MEMBER_SIZE = 100
        const val MAX_NEED_MEMBER_SKILL_LENGTH = 64
        const val MAX_NEED_MEMBER_CONTEXT_LENGTH = 255
        const val MAX_LEADER_MC_ID_LENGTH = 64
        const val MAX_CONTACT_INFORMATION_LENGTH = 255
        const val MAX_COVER_IMAGE_URL_LENGTH = 512
        const val MAX_PAGE_SIZE = 1024
    }
}
