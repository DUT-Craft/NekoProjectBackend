package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemCommentPageVO(
    val content: List<ObjectItemCommentResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

/** 项目评论管理业务：统一 JWT 鉴权（项目 OWNER/MANAGER 或超管，由 AccessService.ensureCanManage 校验）。 */
@Service
class ObjectItemCommentManagementService(
    private val objectItemCommentRepository: ObjectItemCommentRepository,
) {

    @Transactional(readOnly = true)
    fun listByAdmin(
        objectItemId: Int,
        status: ObjectItemCommentStatus?,
    ): List<ObjectItemCommentResponse> {
        val page = listByAdminPage(objectItemId, status, 0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目评论超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun listByAdminPage(
        objectItemId: Int,
        status: ObjectItemCommentStatus?,
        page: Int,
        size: Int,
    ): ObjectItemCommentPageVO {
        requirePositiveItemId(objectItemId)
        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                if (status != null) {
                    objectItemCommentRepository.findByObjectItemIdAndStatus(objectItemId, status, pageable)
                } else {
                    objectItemCommentRepository.findByObjectItemId(objectItemId, pageable)
                }
            },
            count = {
                if (status != null) {
                    objectItemCommentRepository.countByObjectItemIdAndStatus(objectItemId, status)
                } else {
                    objectItemCommentRepository.countByObjectItemId(objectItemId)
                }
            },
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

    private fun requirePositiveItemId(objectItemId: Int) {
        if (objectItemId <= 0) throw ParamErrorException("项目条目 ID 必须大于 0")
    }

    @Transactional
    fun reviewByAdmin(
        objectItemId: Int,
        commentId: Int,
        status: ObjectItemCommentStatus,
    ): ObjectItemCommentResponse {
        val comment = loadComment(commentId, objectItemId)
        comment.status = status
        return objectItemCommentRepository.save(comment).toResponse()
    }

    @Transactional
    fun deleteByAdmin(
        objectItemId: Int,
        commentId: Int,
    ) {
        val comment = loadComment(commentId, objectItemId)
        comment.status = ObjectItemCommentStatus.DELETED
        objectItemCommentRepository.save(comment)
    }

    private fun loadComment(commentId: Int, objectItemId: Int): ObjectItemComment {
        if (commentId <= 0) {
            throw ParamErrorException("项目评论 ID 必须大于 0")
        }
        val comment = objectItemCommentRepository.findById(commentId)
            .orElseThrow { ResourceNotFoundException("项目评论不存在") }
        if (comment.objectItemId != objectItemId) {
            throw ResourceNotFoundException("项目评论不存在")
        }
        return comment
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
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
    }
}
