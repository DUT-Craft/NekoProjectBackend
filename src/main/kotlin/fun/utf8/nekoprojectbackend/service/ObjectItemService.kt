package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.*
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

data class NeedMemberItemRequest(
    @field:NotBlank(message = "项目招募技能不能为空")
    @field:Size(max = 64, message = "项目招募技能不能超过 64 个字符")
    val skill: String = "",
    @field:PositiveOrZero(message = "项目招募人数不能小于 0")
    val number: Long? = null,
    @field:Size(max = 255, message = "项目招募说明不能超过 255 个字符")
    val context: String? = null,
)

data class NeedMemberItemResponse(
    val skill: String?,
    val number: Long?,
    val context: String?,
)

data class ObjectItemSaveRequest(
    @field:NotBlank(message = "项目标题不能为空")
    @field:Size(max = 128, message = "项目标题不能超过 128 个字符")
    val title: String = "",
    @field:NotBlank(message = "项目类型不能为空")
    @field:Size(max = 64, message = "项目类型不能超过 64 个字符")
    val type: String = "",
    @field:Size(max = 255, message = "项目简介不能超过 255 个字符")
    val introduction: String? = null,
    @field:Size(max = 20_000, message = "项目描述不能超过 20000 个字符")
    val description: String? = null,
    // 未指定时为 null：公开投稿由 toEntity() 固化为 PENDING；管理员创建时由 controller 按角色兜底
    // （总管理默认 RECRUITING、项目管理强制 PENDING）。
    val status: ObjectItemStatus? = null,
    @field:Size(max = 64, message = "项目负责人不能超过 64 个字符")
    val leader: String? = null,
    @field:Size(max = 100, message = "项目招募需求不能超过 100 条")
    @field:Valid
    val needMembers: List<NeedMemberItemRequest>? = null,
    @field:Size(max = 12, message = "项目标签不能超过 12 个")
    val tags: List<String>? = null,
    @field:Size(max = 64, message = "负责人 Minecraft ID 不能超过 64 个字符")
    val leaderMcId: String? = null,
    @field:Size(max = 255, message = "联系方式不能超过 255 个字符")
    val contactInformation: String? = null,
    @field:Size(max = 512, message = "封面图地址不能超过 512 个字符")
    val coverImageUrl: String? = null,
    @field:Min(value = 0, message = "项目进度不能小于 0")
    @field:Max(value = 100, message = "项目进度不能大于 100")
    val progress: Int? = 0,
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String? = null,
)

data class ObjectItemBatchSaveRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    @field:Valid
    val items: List<ObjectItemSaveRequest> = emptyList(),
)

data class ObjectItemUpdateRequest(
    val id: Int? = null,
    @field:Size(max = 128, message = "项目标题不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 64, message = "项目类型不能超过 64 个字符")
    val type: String? = null,
    @field:Size(max = 255, message = "项目简介不能超过 255 个字符")
    val introduction: String? = null,
    @field:Size(max = 20_000, message = "项目描述不能超过 20000 个字符")
    val description: String? = null,
    val status: ObjectItemStatus? = null,
    @field:Size(max = 64, message = "项目负责人不能超过 64 个字符")
    val leader: String? = null,
    @field:Size(max = 100, message = "项目招募需求不能超过 100 条")
    @field:Valid
    val needMembers: List<NeedMemberItemRequest>? = null,
    @field:Size(max = 12, message = "项目标签不能超过 12 个")
    val tags: List<String>? = null,
    @field:Size(max = 64, message = "负责人 Minecraft ID 不能超过 64 个字符")
    val leaderMcId: String? = null,
    @field:Size(max = 255, message = "联系方式不能超过 255 个字符")
    val contactInformation: String? = null,
    @field:Size(max = 512, message = "封面图地址不能超过 512 个字符")
    val coverImageUrl: String? = null,
    @field:Min(value = 0, message = "项目进度不能小于 0")
    @field:Max(value = 100, message = "项目进度不能大于 100")
    val progress: Int? = null,
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String? = null,
)

data class ObjectItemBatchUpdateRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    @field:Valid
    val items: List<ObjectItemUpdateRequest> = emptyList(),
)

data class ObjectItemBatchDeleteRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    val ids: List<Int> = emptyList(),
)

data class ObjectItemQueryRequest(
    @field:Size(max = 100, message = "项目 ID 查询不能超过 100 个")
    val ids: List<Int>? = null,
    @field:Size(max = 128, message = "项目标题查询不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 64, message = "项目类型查询不能超过 64 个字符")
    val type: String? = null,
    val status: ObjectItemStatus? = null,
    @field:Size(max = 8, message = "项目状态查询不能超过 8 个")
    val statuses: List<ObjectItemStatus>? = null,
    @field:Size(max = 64, message = "项目负责人查询不能超过 64 个字符")
    val leader: String? = null,
    @field:Size(max = 64, message = "Minecraft ID 查询不能超过 64 个字符")
    val leaderMcId: String? = null,
    @field:Size(max = 12, message = "项目标签查询不能超过 12 个")
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
    val progress: Int,
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
    private val passwordEncoder: PasswordEncoder,
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

    /** 公开详情只返回已经进入公开运营状态的项目；其他状态统一按不存在处理。 */
    @Transactional(readOnly = true)
    fun findPublicById(id: Int): ObjectItemResponse {
        val item = findObjectItem(id)
        ensurePublicStatus(item)
        return item.toResponse()
    }

    /** 供评论、动态和加入申请等公开子资源复用同一项目可见性判定。 */
    @Transactional(readOnly = true)
    fun ensurePubliclyAvailable(id: Int) {
        ensurePublicStatus(findObjectItem(id))
    }

    @Transactional(readOnly = true)
    fun query(request: ObjectItemQueryRequest): List<ObjectItemResponse> {
        val result = objectItemRepository.findAll(
            specification(request),
            PageRequest.of(0, MAX_UNPAGED_RESULTS, Sort.by(Sort.Direction.ASC, "id")),
        )
        if (result.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("匹配项目超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return result.content.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun queryPublic(request: ObjectItemQueryRequest): List<ObjectItemResponse> {
        val publicRequest = publicQueryRequest(request) ?: return emptyList()
        return query(publicRequest)
    }

    @Transactional(readOnly = true)
    fun queryPage(request: ObjectItemQueryRequest, page: Int, size: Int, sort: String): ObjectItemPageVO {
        validatePageRequest(page, size)
        val (property, direction) = parseSort(sort)
        val springDirection = if (direction == SortDirection.DESC) Sort.Direction.DESC else Sort.Direction.ASC
        val specification = specification(request)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = objectItemRepository.count(specification)
            return ObjectItemPageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }
        val result = objectItemRepository.findAll(
            specification,
            PageRequest.of(page, size, Sort.by(springDirection, property.alias)),
        )

        return ObjectItemPageVO(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = page,
            size = size,
        )
    }

    @Transactional(readOnly = true)
    fun queryPublicPage(
        request: ObjectItemQueryRequest,
        page: Int,
        size: Int,
        sort: String,
    ): ObjectItemPageVO {
        val publicRequest = publicQueryRequest(request)
        if (publicRequest == null) {
            validatePageRequest(page, size)
            parseSort(sort)
            return ObjectItemPageVO(
                content = emptyList(),
                totalElements = 0,
                totalPages = 0,
                page = page,
                size = size,
            )
        }
        return queryPage(publicRequest, page, size, sort)
    }

    private fun specification(request: ObjectItemQueryRequest): Specification<ObjectItem> {
        val ids = normalizeIds(request.ids)
        val normalizedTitle = normalizeNullableText(
            request.title,
            MAX_TITLE_LENGTH,
            "项目标题查询不能超过 $MAX_TITLE_LENGTH 个字符",
        )
        val normalizedType = normalizeNullableText(
            request.type,
            MAX_TYPE_LENGTH,
            "项目类型查询不能超过 $MAX_TYPE_LENGTH 个字符",
        )
        val normalizedLeader = normalizeNullableText(
            request.leader,
            MAX_LEADER_LENGTH,
            "项目负责人查询不能超过 $MAX_LEADER_LENGTH 个字符",
        )
        val normalizedLeaderMcId = normalizeNullableText(
            request.leaderMcId,
            MAX_LEADER_MC_ID_LENGTH,
            "Minecraft ID 查询不能超过 $MAX_LEADER_MC_ID_LENGTH 个字符",
        )
        val normalizedTags = cleanTags(request.tags)
        val requestedStatuses = normalizeStatuses(request.status, request.statuses)
        if (request.ownerId != null && request.ownerId <= 0) {
            throw ParamErrorException("项目归属用户 ID 必须大于 0")
        }

        return Specification { root, query, criteriaBuilder ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            ids?.takeIf { it.isNotEmpty() }?.let {
                predicates += root.get<Int>("id").`in`(it)
            }
            normalizedTitle?.let {
                predicates += criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("title")),
                    containsPattern(it),
                    LIKE_ESCAPE,
                )
            }
            normalizedType?.let {
                predicates += criteriaBuilder.equal(criteriaBuilder.lower(root.get<String>("type")), it.lowercase())
            }
            requestedStatuses.takeIf { it.isNotEmpty() }?.let {
                predicates += root.get<ObjectItemStatus>("status").`in`(it)
            }
            normalizedLeader?.let {
                predicates += criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("leader")),
                    containsPattern(it),
                    LIKE_ESCAPE,
                )
            }
            normalizedLeaderMcId?.let {
                predicates += criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get<String>("leaderMcId")),
                    it.lowercase(),
                )
            }
            normalizedTags.forEach { tag ->
                val subquery = query.subquery(Int::class.java)
                val tagRoot = subquery.from(ObjectItem::class.java)
                val tagJoin = tagRoot.join<ObjectItem, String>("tags")
                subquery.select(tagRoot.get("id"))
                    .where(
                        criteriaBuilder.equal(tagRoot.get<Int>("id"), root.get<Int>("id")),
                        criteriaBuilder.equal(criteriaBuilder.lower(tagJoin), tag.lowercase()),
                    )
                predicates += criteriaBuilder.exists(subquery)
            }
            request.ownerId?.let {
                predicates += criteriaBuilder.equal(root.get<Long>("ownerId"), it)
            }
            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    private fun normalizeStatuses(status: ObjectItemStatus?, statuses: List<ObjectItemStatus>?): Set<ObjectItemStatus> {
        if (statuses != null && statuses.size > ObjectItemStatus.entries.size) {
            throw ParamErrorException("项目状态查询不能超过 ${ObjectItemStatus.entries.size} 个")
        }
        return (listOfNotNull(status) + statuses.orEmpty()).toSet()
    }

    private fun publicQueryRequest(request: ObjectItemQueryRequest): ObjectItemQueryRequest? {
        val requestedStatuses = normalizeStatuses(request.status, request.statuses)
        val effectiveStatuses = if (requestedStatuses.isEmpty()) {
            PUBLIC_STATUSES
        } else {
            requestedStatuses.intersect(PUBLIC_STATUSES)
        }
        if (effectiveStatuses.isEmpty()) {
            return null
        }
        return request.copy(status = null, statuses = effectiveStatuses.toList())
    }

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0) {
            throw ParamErrorException("页码不能小于 0")
        }
        if (size <= 0) {
            throw ParamErrorException("每页条数必须大于 0")
        }
        if (size > MAX_PAGE_SIZE) {
            throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")
        }
    }

    private fun totalPages(totalElements: Long, size: Int): Int =
        if (totalElements == 0L) {
            0
        } else {
            (((totalElements - 1) / size) + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    private fun parseSort(sort: String): Pair<ObjectItemSortProperty, SortDirection> {
        val parts = sort.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return ObjectItemSortProperty.ID to SortDirection.DESC
        }
        if (parts.size > 2) {
            throw ParamErrorException("排序参数格式错误，应为 字段,方向")
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
        return query(ObjectItemQueryRequest(status = status))
    }

    @Transactional(readOnly = true)
    fun findPublicByStatus(status: ObjectItemStatus): List<ObjectItemResponse> {
        if (status !in PUBLIC_STATUSES) {
            return emptyList()
        }
        return findByStatus(status)
    }

    @Transactional(readOnly = true)
    fun countInProgress(): Long {
        return objectItemRepository.countByStatus(ObjectItemStatus.IN_PROGRESS)
    }

    @Transactional(readOnly = true)
    fun countPublic(): Long {
        return objectItemRepository.countByStatusIn(PUBLIC_STATUSES)
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
            if (ownerId <= 0) {
                throw ParamErrorException("项目归属用户 ID 必须大于 0")
            }
            val owner = userRepository.findById(ownerId)
                .orElseThrow { ResourceNotFoundException("用户不存在") }
            if (owner.status != Status.ACTIVE || owner.role !in ASSIGNABLE_OWNER_ROLES) {
                throw ParamErrorException("项目只能分配给状态正常的项目管理或总管理账号")
            }
            // 重新分配给同一人不重复计入上限
            val alreadyOwned = item.ownerId == ownerId
            if (!alreadyOwned && countOwnedProjects(ownerId) >= maxPerManager) {
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
        if (countOwnedProjects(ownerId) >= maxPerManager) {
            throw ParamErrorException("名下项目已达上限 $maxPerManager")
        }
        val entity = request.toEntity()
        entity.status = normalizeStatus(status)
        entity.ownerId = ownerId
        return objectItemRepository.save(entity).toResponse()
    }

    private fun countOwnedProjects(ownerId: Long): Long =
        objectItemRepository.countByOwnerIdAndStatusNot(ownerId, ObjectItemStatus.DELETED)

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
            it.description = normalizeNullableText(
                description,
                MAX_DESCRIPTION_LENGTH,
                "项目描述不能超过 $MAX_DESCRIPTION_LENGTH 个字符",
            )
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
            it.coverImageUrl = ImageUrlPolicy.normalize(
                coverImageUrl,
                MAX_COVER_IMAGE_URL_LENGTH,
                "封面图地址",
            )
            it.progress = normalizeProgress(progress)
            it.controlPassword = encodeControlPassword(controlPassword)
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
        request.description?.let {
            description = normalizeNullableText(
                it,
                MAX_DESCRIPTION_LENGTH,
                "项目描述不能超过 $MAX_DESCRIPTION_LENGTH 个字符",
            )
        }
        request.status?.let { status = normalizeStatus(it) }
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
            coverImageUrl = ImageUrlPolicy.normalize(
                it,
                MAX_COVER_IMAGE_URL_LENGTH,
                "封面图地址",
            )
        }
        request.progress?.let { progress = normalizeProgress(it) }
        request.controlPassword?.let {
            controlPassword = encodeControlPassword(it)
        }
    }

    private fun encodeControlPassword(value: String?): String? {
        val normalized = ProjectControlPasswordPolicy.normalizeOptional(value)
        return normalized?.let(passwordEncoder::encode)
    }

    private fun ensurePublicStatus(item: ObjectItem) {
        if (item.status !in PUBLIC_STATUSES) {
            throw ResourceNotFoundException("项目条目不存在")
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
        if (ids.size > MAX_QUERY_ID_COUNT) {
            throw ParamErrorException("项目 ID 查询不能超过 $MAX_QUERY_ID_COUNT 个")
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

    private fun normalizeProgress(value: Int?): Int {
        val progress = value ?: 0
        if (progress !in 0..100) {
            throw ParamErrorException("项目进度必须在 0 到 100 之间")
        }
        return progress
    }

    private fun normalizeStatus(value: ObjectItemStatus): ObjectItemStatus =
        if (value == ObjectItemStatus.APPROVED) ObjectItemStatus.PREPARING else value

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
        val source = tags.orEmpty()
        if (source.size > MAX_TAG_COUNT) {
            throw ParamErrorException("项目标签不能超过 $MAX_TAG_COUNT 个")
        }
        return source
            .mapNotNull { normalizeNullableText(it, MAX_TAG_LENGTH, "项目标签不能超过 $MAX_TAG_LENGTH 个字符") }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun containsPattern(value: String): String {
        val escaped = value.lowercase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
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
            progress = progress,
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
        private val PUBLIC_STATUSES = setOf(
            ObjectItemStatus.PREPARING,
            ObjectItemStatus.RECRUITING,
            ObjectItemStatus.IN_PROGRESS,
            ObjectItemStatus.PAUSED,
            // Keep legacy approved rows visible until they are migrated.
            ObjectItemStatus.APPROVED,
        )
        private val ASSIGNABLE_OWNER_ROLES = setOf(Role.PROJECT_MANAGER, Role.SUPER_ADMIN)
        private const val MAX_BATCH_SIZE = 100
        private const val MAX_TITLE_LENGTH = 128
        private const val MAX_TYPE_LENGTH = 64
        private const val MAX_INTRODUCTION_LENGTH = 255
        private const val MAX_DESCRIPTION_LENGTH = 20_000
        private const val MAX_LEADER_LENGTH = 64
        private const val MAX_NEED_MEMBER_SIZE = 100
        private const val MAX_NEED_MEMBER_SKILL_LENGTH = 64
        private const val MAX_NEED_MEMBER_CONTEXT_LENGTH = 255
        private const val MAX_LEADER_MC_ID_LENGTH = 64
        private const val MAX_CONTACT_INFORMATION_LENGTH = 255
        private const val MAX_COVER_IMAGE_URL_LENGTH = 512
        private const val MAX_TAG_LENGTH = 32
        private const val MAX_TAG_COUNT = 12
        private const val MAX_QUERY_ID_COUNT = 100
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
        private const val LIKE_ESCAPE = '\\'
    }
}
