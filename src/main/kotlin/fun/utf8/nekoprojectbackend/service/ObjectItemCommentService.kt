package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class ObjectItemCommentSaveRequest(
    @field:NotBlank(message = "评论者昵称不能为空")
    @field:Size(max = 64, message = "评论者昵称不能超过 64 个字符")
    val nickName: String = "",
    @field:NotBlank(message = "评论内容不能为空")
    @field:Size(max = 2000, message = "评论内容不能超过 2000 个字符")
    val content: String = "",
)

data class ObjectItemCommentResponse(
    val id: Int?,
    val objectItemId: Int?,
    val nickName: String?,
    val content: String?,
    val status: ObjectItemCommentStatus?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

data class ObjectItemCommentPageVO(
    val content: List<ObjectItemCommentResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

/** 项目评论业务：公开评论的创建与查询（查询默认仅返回已通过）。 */
@Service
class ObjectItemCommentService(
    private val objectItemService: ObjectItemService,
    private val objectItemCommentRepository: ObjectItemCommentRepository,
) {

    @Transactional(readOnly = true)
    fun findByObjectItem(
        objectItemId: Int,
    ): List<ObjectItemCommentResponse> {
        val page = findByObjectItemPage(objectItemId, 0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目评论超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun findByObjectItemPage(
        objectItemId: Int,
        page: Int,
        size: Int,
    ): ObjectItemCommentPageVO {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        objectItemService.ensurePubliclyAvailable(resolvedItemId)

        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                objectItemCommentRepository.findByObjectItemIdAndStatus(
                    resolvedItemId,
                    ObjectItemCommentStatus.APPROVED,
                    pageable,
                )
            },
            count = {
                objectItemCommentRepository.countByObjectItemIdAndStatus(
                    resolvedItemId,
                    ObjectItemCommentStatus.APPROVED,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun findApproved(): List<ObjectItemCommentResponse> {
        val page = findApprovedPage(0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目评论超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun findApprovedPage(page: Int, size: Int): ObjectItemCommentPageVO {
        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                objectItemCommentRepository.findByStatus(ObjectItemCommentStatus.APPROVED, pageable)
            },
            count = { objectItemCommentRepository.countByStatus(ObjectItemCommentStatus.APPROVED) },
        )
    }

    private fun queryPage(
        page: Int,
        size: Int,
        query: (Pageable) -> Page<ObjectItemComment>,
        count: () -> Long,
    ): ObjectItemCommentPageVO {
        validatePageRequest(page, size)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = count()
            return ObjectItemCommentPageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }

        val result = query(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
        return ObjectItemCommentPageVO(
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

    @Transactional
    fun create(objectItemId: Int, request: ObjectItemCommentSaveRequest): ObjectItemCommentResponse {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        objectItemService.ensurePubliclyAvailable(resolvedItemId)

        val entity = ObjectItemComment().also {
            it.objectItemId = resolvedItemId
            it.nickName = requireText(
                request.nickName,
                "评论者昵称不能为空",
                MAX_NICK_NAME_LENGTH,
                "评论者昵称不能超过 $MAX_NICK_NAME_LENGTH 个字符",
            )
            it.content = requireText(
                request.content,
                "评论内容不能为空",
                MAX_CONTENT_LENGTH,
                "评论内容不能超过 $MAX_CONTENT_LENGTH 个字符",
            )
            it.status = ObjectItemCommentStatus.PENDING
        }
        return objectItemCommentRepository.save(entity).toResponse()
    }

    private fun requirePositiveItemId(objectItemId: Int): Int {
        if (objectItemId <= 0) {
            throw ParamErrorException("项目条目 ID 必须大于 0")
        }
        return objectItemId
    }

    private fun requireText(value: String, blankMessage: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw ParamErrorException(blankMessage)
        }
        return normalized
    }

    private fun requireText(value: String, blankMessage: String, maxLength: Int, tooLongMessage: String): String {
        val normalized = requireText(value, blankMessage)
        if (normalized.length > maxLength) {
            throw ParamErrorException(tooLongMessage)
        }
        return normalized
    }

    private fun ObjectItemComment.toResponse(): ObjectItemCommentResponse {
        return ObjectItemCommentResponse(
            id = id,
            objectItemId = objectItemId,
            nickName = nickName,
            content = content,
            status = status,
            createTime = createTime,
            updateTime = updateTime,
        )
    }

    private companion object {
        private const val MAX_NICK_NAME_LENGTH = 64
        private const val MAX_CONTENT_LENGTH = 2_000
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
    }
}
