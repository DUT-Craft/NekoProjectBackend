package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdate
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class ObjectItemUpdateResponse(
    val id: Int?,
    val objectItemId: Int?,
    val title: String?,
    val content: String?,
    val imageUrl: String?,
    val status: ObjectItemUpdateStatus?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

data class ObjectItemUpdatePageVO(
    val content: List<ObjectItemUpdateResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

/** 项目动态业务：动态查询（默认仅返回已通过）。 */
@Service
class ObjectItemUpdateService(
    private val objectItemService: ObjectItemService,
    private val objectItemUpdateRepository: ObjectItemUpdateRepository,
) {

    @Transactional(readOnly = true)
    fun findByObjectItem(
        objectItemId: Int,
        @Suppress("UNUSED_PARAMETER") status: ObjectItemUpdateStatus? = null,
    ): List<ObjectItemUpdateResponse> {
        val page = findByObjectItemPage(objectItemId, 0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目动态超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun findByObjectItemPage(
        objectItemId: Int,
        page: Int,
        size: Int,
    ): ObjectItemUpdatePageVO {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        objectItemService.ensurePubliclyAvailable(resolvedItemId)

        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                objectItemUpdateRepository.findByObjectItemIdAndStatus(
                    resolvedItemId,
                    ObjectItemUpdateStatus.APPROVED,
                    pageable,
                )
            },
            count = {
                objectItemUpdateRepository.countByObjectItemIdAndStatus(
                    resolvedItemId,
                    ObjectItemUpdateStatus.APPROVED,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun findApproved(): List<ObjectItemUpdateResponse> {
        val page = findApprovedPage(0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目动态超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun findApprovedPage(page: Int, size: Int): ObjectItemUpdatePageVO {
        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                objectItemUpdateRepository.findByStatus(ObjectItemUpdateStatus.APPROVED, pageable)
            },
            count = { objectItemUpdateRepository.countByStatus(ObjectItemUpdateStatus.APPROVED) },
        )
    }

    private fun queryPage(
        page: Int,
        size: Int,
        query: (Pageable) -> Page<ObjectItemUpdate>,
        count: () -> Long,
    ): ObjectItemUpdatePageVO {
        validatePageRequest(page, size)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = count()
            return ObjectItemUpdatePageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }

        val result = query(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
        return ObjectItemUpdatePageVO(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = page,
            size = size,
        )
    }

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0) throw ParamErrorException("页码不能小于 0")
        if (size <= 0) throw ParamErrorException("每页条数必须大于 0")
        if (size > MAX_PAGE_SIZE) throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")
    }

    private fun totalPages(totalElements: Long, size: Int): Int =
        if (totalElements == 0L) 0 else (((totalElements - 1) / size) + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun requirePositiveItemId(objectItemId: Int): Int {
        if (objectItemId <= 0) {
            throw ParamErrorException("项目条目 ID 必须大于 0")
        }
        return objectItemId
    }

    private fun ObjectItemUpdate.toResponse(): ObjectItemUpdateResponse {
        return ObjectItemUpdateResponse(
            id = id,
            objectItemId = objectItemId,
            title = title,
            content = content,
            imageUrl = imageUrl,
            status = status,
            createTime = createTime,
            updateTime = updateTime,
        )
    }

    private companion object {
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
    }
}
