package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.ModerationProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Mind
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class MindSaveRequest(
    val title: String = "",
    val nickName: String? = null,
    val status: MindStatus? = MindStatus.PENDING,
    val content: String? = null,
    val mcId: String? = null,
)

data class MindBatchSaveRequest(
    val items: List<MindSaveRequest> = emptyList(),
)

data class MindUpdateRequest(
    val id: Int? = null,
    val title: String? = null,
    val nickName: String? = null,
    val status: MindStatus? = null,
    val content: String? = null,
    val mcId: String? = null,
)

data class MindBatchUpdateRequest(
    val items: List<MindUpdateRequest> = emptyList(),
)

data class MindBatchDeleteRequest(
    val ids: List<Int> = emptyList(),
)

data class MindQueryRequest(
    val ids: List<Int>? = null,
    val title: String? = null,
    val nickName: String? = null,
    val status: MindStatus? = null,
    val statuses: List<MindStatus>? = null,
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

/** 想法业务：增删改查、批量操作、分页/排序、多条件过滤与字段长度校验。新建想法按 neko.moderation.enabled 决定初始状态（开启审核→PENDING，关闭审核→APPROVED 直接公开）。 */
@Service
class MindService(
    private val mindRepository: MindRepository,
    private val moderationProperties: ModerationProperties,
) {

    @Transactional
    fun save(request: MindSaveRequest): MindResponse =
        mindRepository.save(request.toEntity()).toResponse()

    @Transactional
    fun saveBatch(requests: List<MindSaveRequest>): List<MindResponse> {
        validateBatchSize(requests, "批量保存想法不能为空")
        return mindRepository.saveAll(requests.map { it.toEntity() })
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findById(id: Int): MindResponse = findMind(id).toResponse()

    @Transactional(readOnly = true)
    fun query(request: MindQueryRequest): List<MindResponse> =
        filterMinds(request).sortedWith(mindComparator(DEFAULT_SORT))
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun queryPage(request: MindQueryRequest, page: Int, size: Int, sort: String): MindPageVO {
        if (page < 0) throw ParamErrorException("页码不能小于 0")
        if (size <= 0) throw ParamErrorException("每页条数必须大于 0")
        if (size > MAX_PAGE_SIZE) throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")

        val sorted = filterMinds(request).sortedWith(mindComparator(sort))
        val total = sorted.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        val fromIndex = minOf(page * size, sorted.size)
        val toIndex = minOf(fromIndex + size, sorted.size)
        val pageContent = sorted.subList(fromIndex, toIndex).map { it.toResponse() }

        return MindPageVO(
            content = pageContent,
            totalElements = total,
            totalPages = totalPages,
            page = page,
            size = size,
        )
    }

    @Transactional(readOnly = true)
    fun findByStatus(status: MindStatus): List<MindResponse> =
        mindRepository.findByStatus(status)
            .sortedWith(mindComparator(DEFAULT_SORT))
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun findByStatuses(statuses: List<MindStatus>): List<MindResponse> {
        if (statuses.isEmpty()) throw ParamErrorException("状态列表不能为空")
        return mindRepository.findByStatusIn(statuses)
            .sortedWith(mindComparator(DEFAULT_SORT))
            .map { it.toResponse() }
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

    private fun filterMinds(request: MindQueryRequest): List<Mind> {
        val ids = normalizeIds(request.ids)
        val normalizedTitle = normalizeNullableText(request.title)
        val normalizedNickName = normalizeNullableText(request.nickName)
        val normalizedMcId = normalizeNullableText(request.mcId)
        val requestedStatuses = normalizeStatuses(request.status, request.statuses)
        val filterByStatus = requestedStatuses.isNotEmpty()

        val source = if (ids.isNullOrEmpty()) {
            mindRepository.findAll()
        } else {
            mindRepository.findAllById(ids).toList()
        }

        return source.asSequence()
            .filter { normalizedTitle == null || it.title?.contains(normalizedTitle, ignoreCase = true) == true }
            .filter {
                normalizedNickName == null || it.nickName?.contains(
                    normalizedNickName,
                    ignoreCase = true
                ) == true
            }
            .filter { !filterByStatus || it.status in requestedStatuses }
            .filter { normalizedMcId == null || it.mcId?.equals(normalizedMcId, ignoreCase = true) == true }
            .toList()
    }

    private fun mindComparator(sort: String): Comparator<Mind> {
        val (property, direction) = parseSort(sort)
        val base: Comparator<Mind> = when (property) {
            MindSortProperty.ID -> compareBy { it.id ?: Int.MAX_VALUE }
            MindSortProperty.CREATE_TIME -> compareBy { it.createTime ?: LocalDateTime.MIN }
            MindSortProperty.UPDATE_TIME -> compareBy { it.updateTime ?: LocalDateTime.MIN }
        }
        return if (direction == SortDirection.DESC) base.reversed() else base
    }

    private fun parseSort(sort: String): Pair<MindSortProperty, SortDirection> {
        val parts = sort.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return MindSortProperty.CREATE_TIME to SortDirection.DESC
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
        it.status = if (moderationProperties.enabled) MindStatus.PENDING else MindStatus.APPROVED
        it.content = requireText(content ?: "", "想法内容不能为空")
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
        request.content?.let { content = requireText(it, "想法内容不能为空") }
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
        return ids.map { requirePositiveId(it) }
            .distinct()
            .takeIf { it.isNotEmpty() }
    }

    private fun normalizeStatuses(status: MindStatus?, statuses: List<MindStatus>?): Set<MindStatus> =
        (listOfNotNull(status) + statuses.orEmpty()).toSet()

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
        private const val MAX_NICK_NAME_LENGTH = 64
        private const val MAX_MC_ID_LENGTH = 64
        private const val MAX_PAGE_SIZE = 1024
    }
}
