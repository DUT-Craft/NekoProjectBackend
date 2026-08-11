package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Mind
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class MindSaveRequest(
    @field:NotBlank(message = "想法标题不能为空")
    @field:Size(max = 128, message = "想法标题不能超过 128 个字符")
    val title: String = "",
    @field:Size(max = 64, message = "想法昵称不能超过 64 个字符")
    val nickName: String? = null,
    val status: MindStatus? = MindStatus.PENDING,
    @field:NotBlank(message = "想法内容不能为空")
    @field:Size(max = 10000, message = "想法内容不能超过 10000 个字符")
    val content: String? = null,
    @field:Size(max = 64, message = "想法 Minecraft ID 不能超过 64 个字符")
    val mcId: String? = null,
)

data class MindBatchSaveRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    @field:Valid
    val items: List<MindSaveRequest> = emptyList(),
)

data class MindUpdateRequest(
    val id: Int? = null,
    @field:Size(max = 128, message = "想法标题不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 64, message = "想法昵称不能超过 64 个字符")
    val nickName: String? = null,
    val status: MindStatus? = null,
    @field:Size(max = 10000, message = "想法内容不能超过 10000 个字符")
    val content: String? = null,
    @field:Size(max = 64, message = "想法 Minecraft ID 不能超过 64 个字符")
    val mcId: String? = null,
)

data class MindBatchUpdateRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    @field:Valid
    val items: List<MindUpdateRequest> = emptyList(),
)

data class MindBatchDeleteRequest(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    val ids: List<Int> = emptyList(),
)

data class MindQueryRequest(
    @field:Size(max = 100, message = "想法 ID 查询不能超过 100 个")
    val ids: List<Int>? = null,
    @field:Size(max = 128, message = "想法标题查询不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 64, message = "想法昵称查询不能超过 64 个字符")
    val nickName: String? = null,
    val status: MindStatus? = null,
    @field:Size(max = 4, message = "想法状态查询不能超过 4 个")
    val statuses: List<MindStatus>? = null,
    @field:Size(max = 64, message = "Minecraft ID 查询不能超过 64 个字符")
    val mcId: String? = null,
)

data class MindResponse(
    val id: Int?,
    val title: String?,
    val nickName: String?,
    val status: MindStatus?,
    val content: String?,
    val mcId: String?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

data class MindPageVO(
    val content: List<MindResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

enum class MindSortProperty(val alias: String) {
    ID("id"),
    CREATE_TIME("createTime"),
    UPDATE_TIME("updateTime"),
    ;

    companion object {
        fun from(value: String): MindSortProperty? =
            entries.firstOrNull { it.alias.equals(value, ignoreCase = true) }
    }
}

// SortDirection 定义于 ObjectItemService.kt，此处不再重复声明，避免同包重复定义。

/** 想法业务：增删改查、批量操作、分页/排序、多条件过滤与字段长度校验。新建想法固定 PENDING 待审。 */
@Service
class MindService(
    private val mindRepository: MindRepository,
    private val submissionTrackingService: SubmissionTrackingService,
) {

    @Transactional
    fun save(request: MindSaveRequest): MindResponse =
        mindRepository.save(request.toEntity()).toResponse()

    @Transactional
    fun saveTracked(request: MindSaveRequest): TrackedSubmission<MindResponse> {
        val issued = submissionTrackingService.issue()
        val entity = request.toEntity().also { it.trackingTokenHash = issued.hash }
        return TrackedSubmission(
            value = mindRepository.save(entity).toResponse(),
            trackingToken = issued.token,
        )
    }

    @Transactional
    fun saveBatch(requests: List<MindSaveRequest>): List<MindResponse> {
        validateBatchSize(requests, "批量保存想法不能为空")
        return mindRepository.saveAll(requests.map { it.toEntity() })
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findById(id: Int): MindResponse = findMind(id).toResponse()

    @Transactional(readOnly = true)
    fun findPublicById(id: Int): MindResponse {
        val mind = findMind(id)
        if (mind.status != MindStatus.APPROVED) {
            throw ResourceNotFoundException("想法不存在")
        }
        return mind.toResponse()
    }

    @Transactional(readOnly = true)
    fun findTracked(id: Int, trackingToken: String): MindResponse {
        val mind = findMind(id)
        if (!submissionTrackingService.matches(trackingToken, mind.trackingTokenHash)) {
            throw ResourceNotFoundException("想法不存在或追踪码不正确")
        }
        return mind.toResponse()
    }

    @Transactional(readOnly = true)
    fun query(request: MindQueryRequest): List<MindResponse> =
        mindRepository.findAll(
            specification(request),
            PageRequest.of(0, MAX_UNPAGED_RESULTS, toSpringSort(DEFAULT_SORT)),
        ).let { result ->
            if (result.totalElements > MAX_UNPAGED_RESULTS) {
                throw ParamErrorException("匹配想法超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
            }
            result.content.map { it.toResponse() }
        }

    @Transactional(readOnly = true)
    fun queryPublic(request: MindQueryRequest): List<MindResponse> =
        query(request.copy(status = MindStatus.APPROVED, statuses = null))

    @Transactional(readOnly = true)
    fun queryPage(request: MindQueryRequest, page: Int, size: Int, sort: String): MindPageVO {
        if (page < 0) throw ParamErrorException("页码不能小于 0")
        if (size <= 0) throw ParamErrorException("每页条数必须大于 0")
        if (size > MAX_PAGE_SIZE) throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")

        val specification = specification(request)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = mindRepository.count(specification)
            return MindPageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }
        val result = mindRepository.findAll(
            specification,
            PageRequest.of(page, size, toSpringSort(sort)),
        )

        return MindPageVO(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = page,
            size = size,
        )
    }

    @Transactional(readOnly = true)
    fun queryPublicPage(request: MindQueryRequest, page: Int, size: Int, sort: String): MindPageVO =
        queryPage(request.copy(status = MindStatus.APPROVED, statuses = null), page, size, sort)

    @Transactional(readOnly = true)
    fun findByStatus(status: MindStatus): List<MindResponse> =
        query(MindQueryRequest(status = status))

    @Transactional(readOnly = true)
    fun findPublicByStatus(status: MindStatus): List<MindResponse> =
        if (status == MindStatus.APPROVED) findByStatus(status) else emptyList()

    @Transactional(readOnly = true)
    fun findByStatuses(statuses: List<MindStatus>): List<MindResponse> {
        if (statuses.isEmpty()) throw ParamErrorException("状态列表不能为空")
        return query(MindQueryRequest(statuses = statuses))
    }

    @Transactional(readOnly = true)
    fun findPublicByStatuses(statuses: List<MindStatus>): List<MindResponse> {
        if (statuses.isEmpty()) throw ParamErrorException("状态列表不能为空")
        return if (MindStatus.APPROVED in statuses) findByStatus(MindStatus.APPROVED) else emptyList()
    }

    @Transactional(readOnly = true)
    fun countApproved(): Long = mindRepository.countByStatus(MindStatus.APPROVED)

    @Transactional
    fun update(id: Int, request: MindUpdateRequest): MindResponse {
        val mind = findMind(id)
        mind.applyUpdate(request)
        return mindRepository.save(mind).toResponse()
    }

    @Transactional
    fun updateBatch(requests: List<MindUpdateRequest>): List<MindResponse> {
        validateBatchSize(requests, "批量更新想法不能为空")

        val ids = requests.map {
            val id = it.id ?: throw ParamErrorException("批量更新时想法 ID 不能为空")
            requirePositiveId(id)
        }
        val duplicateIds = ids.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw ParamErrorException("批量更新不能包含重复想法 ID：${duplicateIds.joinToString(", ")}")
        }

        val mindsById = mindRepository.findAllById(ids).associateBy { it.id }
        val missingIds = ids.filter { mindsById[it] == null }
        if (missingIds.isNotEmpty()) {
            throw ResourceNotFoundException("想法不存在：${missingIds.joinToString(", ")}")
        }

        val minds = requests.map { request ->
            val mind = mindsById.getValue(request.id)
            mind.applyUpdate(request)
            mind
        }

        return mindRepository.saveAll(minds).map { it.toResponse() }
    }

    @Transactional
    fun delete(id: Int) {
        update(id, MindUpdateRequest(id = id, status = MindStatus.DELETED))
    }

    @Transactional
    fun deleteBatch(ids: List<Int>) {
        validateBatchSize(ids, "批量删除想法不能为空")
        updateBatch(ids.map { MindUpdateRequest(id = it, status = MindStatus.DELETED) })
    }

    private fun specification(request: MindQueryRequest): Specification<Mind> {
        val ids = normalizeIds(request.ids)
        val normalizedTitle = normalizeNullableText(
            request.title,
            MAX_TITLE_LENGTH,
            "想法标题查询不能超过 $MAX_TITLE_LENGTH 个字符",
        )
        val normalizedNickName = normalizeNullableText(
            request.nickName,
            MAX_NICK_NAME_LENGTH,
            "想法昵称查询不能超过 $MAX_NICK_NAME_LENGTH 个字符",
        )
        val normalizedMcId = normalizeNullableText(
            request.mcId,
            MAX_MC_ID_LENGTH,
            "Minecraft ID 查询不能超过 $MAX_MC_ID_LENGTH 个字符",
        )
        val requestedStatuses = normalizeStatuses(request.status, request.statuses)
        return Specification { root, _, criteriaBuilder ->
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
            normalizedNickName?.let {
                predicates += criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("nickName")),
                    containsPattern(it),
                    LIKE_ESCAPE,
                )
            }
            requestedStatuses.takeIf { it.isNotEmpty() }?.let {
                predicates += root.get<MindStatus>("status").`in`(it)
            }
            normalizedMcId?.let {
                predicates += criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get<String>("mcId")),
                    it.lowercase(),
                )
            }
            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    private fun toSpringSort(sort: String): Sort {
        val (property, direction) = parseSort(sort)
        val springDirection = if (direction == SortDirection.DESC) Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(springDirection, property.alias)
    }

    private fun totalPages(totalElements: Long, size: Int): Int =
        if (totalElements == 0L) {
            0
        } else {
            (((totalElements - 1) / size) + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    private fun parseSort(sort: String): Pair<MindSortProperty, SortDirection> {
        val parts = sort.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return MindSortProperty.CREATE_TIME to SortDirection.DESC
        }
        if (parts.size > 2) {
            throw ParamErrorException("排序参数格式错误，应为 字段,方向")
        }
        val property = MindSortProperty.from(parts[0])
            ?: throw ParamErrorException("不支持的排序字段：${parts[0]}，支持 id / createTime / updateTime")
        val direction = if (parts.size > 1) {
            SortDirection.from(parts[1])
                ?: throw ParamErrorException("不支持的排序方向：${parts[1]}，支持 asc / desc")
        } else {
            SortDirection.DESC
        }
        return property to direction
    }

    private fun MindSaveRequest.toEntity(): Mind = Mind().also {
        it.title = requireText(title, "想法标题不能为空", MAX_TITLE_LENGTH, "想法标题不能超过 $MAX_TITLE_LENGTH 个字符")
        it.nickName =
            normalizeNullableText(nickName, MAX_NICK_NAME_LENGTH, "想法昵称不能超过 $MAX_NICK_NAME_LENGTH 个字符")
        it.status = MindStatus.PENDING
        it.content = requireText(
            content ?: "",
            "想法内容不能为空",
            MAX_CONTENT_LENGTH,
            "想法内容不能超过 $MAX_CONTENT_LENGTH 个字符",
        )
        it.mcId = normalizeNullableText(mcId, MAX_MC_ID_LENGTH, "想法 Minecraft ID 不能超过 $MAX_MC_ID_LENGTH 个字符")
    }

    private fun Mind.applyUpdate(request: MindUpdateRequest) {
        request.title?.let {
            title = requireText(it, "想法标题不能为空", MAX_TITLE_LENGTH, "想法标题不能超过 $MAX_TITLE_LENGTH 个字符")
        }
        request.nickName?.let {
            nickName = normalizeNullableText(it, MAX_NICK_NAME_LENGTH, "想法昵称不能超过 $MAX_NICK_NAME_LENGTH 个字符")
        }
        request.status?.let { status = it }
        request.content?.let {
            content = requireText(
                it,
                "想法内容不能为空",
                MAX_CONTENT_LENGTH,
                "想法内容不能超过 $MAX_CONTENT_LENGTH 个字符",
            )
        }
        request.mcId?.let {
            mcId = normalizeNullableText(it, MAX_MC_ID_LENGTH, "想法 Minecraft ID 不能超过 $MAX_MC_ID_LENGTH 个字符")
        }
    }

    private fun findMind(id: Int): Mind =
        mindRepository.findById(requirePositiveId(id))
            .orElseThrow { ResourceNotFoundException("想法不存在") }

    private fun requirePositiveId(id: Int): Int {
        if (id <= 0) throw ParamErrorException("想法 ID 必须大于 0")
        return id
    }

    private fun normalizeIds(ids: List<Int>?): List<Int>? {
        if (ids == null) return null
        if (ids.size > MAX_QUERY_ID_COUNT) {
            throw ParamErrorException("想法 ID 查询不能超过 $MAX_QUERY_ID_COUNT 个")
        }
        return ids.map { requirePositiveId(it) }
            .distinct()
            .takeIf { it.isNotEmpty() }
    }

    private fun normalizeStatuses(status: MindStatus?, statuses: List<MindStatus>?): Set<MindStatus> {
        if (statuses != null && statuses.size > MindStatus.entries.size) {
            throw ParamErrorException("想法状态查询不能超过 ${MindStatus.entries.size} 个")
        }
        return (listOfNotNull(status) + statuses.orEmpty()).toSet()
    }

    private fun <T> validateBatchSize(items: List<T>, emptyMessage: String) {
        if (items.isEmpty()) throw ParamErrorException(emptyMessage)
        if (items.size > MAX_BATCH_SIZE) throw ParamErrorException("单次批量操作不能超过 $MAX_BATCH_SIZE 条")
    }

    private fun requireText(value: String, blankMessage: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) throw ParamErrorException(blankMessage)
        return normalized
    }

    private fun requireText(value: String, blankMessage: String, maxLength: Int, tooLongMessage: String): String {
        val normalized = requireText(value, blankMessage)
        if (normalized.length > maxLength) throw ParamErrorException(tooLongMessage)
        return normalized
    }

    private fun normalizeNullableText(value: String?): String? = value?.trim()?.ifBlank { null }

    private fun normalizeNullableText(value: String?, maxLength: Int, tooLongMessage: String): String? {
        val normalized = normalizeNullableText(value)
        if (normalized != null && normalized.length > maxLength) throw ParamErrorException(tooLongMessage)
        return normalized
    }

    private fun containsPattern(value: String): String {
        val escaped = value.lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun Mind.toResponse(): MindResponse = MindResponse(
        id = id,
        title = title,
        nickName = nickName,
        status = status,
        content = content,
        mcId = mcId,
        createTime = createTime,
        updateTime = updateTime,
    )

    private companion object {
        private const val DEFAULT_SORT = "createTime,desc"
        private const val MAX_BATCH_SIZE = 100
        private const val MAX_TITLE_LENGTH = 128
        private const val MAX_CONTENT_LENGTH = 10_000
        private const val MAX_NICK_NAME_LENGTH = 64
        private const val MAX_MC_ID_LENGTH = 64
        private const val MAX_QUERY_ID_COUNT = 100
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
        private const val LIKE_ESCAPE = '\\'
    }
}
