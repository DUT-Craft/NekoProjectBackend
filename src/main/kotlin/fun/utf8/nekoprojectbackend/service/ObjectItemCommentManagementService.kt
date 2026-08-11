package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemCommentManageStatusRequest(
    @field:NotBlank(message = "项目控制密码不能为空")
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    val status: ObjectItemCommentStatus,
)

/** 项目评论管理业务：凭项目控制密码查看/审核/删除评论，或管理员直接审核状态。 */
@Service
class ObjectItemCommentManagementService(
    private val objectItemManagementService: ObjectItemManagementService,
    private val objectItemCommentRepository: ObjectItemCommentRepository,
) {

    @Transactional(readOnly = true)
    fun list(
        objectItemId: Int,
        status: ObjectItemCommentStatus?,
        request: ObjectItemManageVerifyRequest,
    ): List<ObjectItemCommentResponse> {
        verifyProject(objectItemId, request)
        return listByAdmin(objectItemId, status)
    }

    /** 管理员查看项目评论：JWT 鉴权（由控制器层保证），无需项目控制密码。 */
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
    fun review(
        objectItemId: Int,
        commentId: Int,
        request: ObjectItemCommentManageStatusRequest,
    ): ObjectItemCommentResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
        ensureModerationStatus(request.status)
        val comment = loadComment(commentId, objectItemId)
        comment.status = request.status
        return objectItemCommentRepository.save(comment).toResponse()
    }

    @Transactional
    fun reviewByAdmin(
        objectItemId: Int,
        commentId: Int,
        status: ObjectItemCommentStatus,
    ): ObjectItemCommentResponse {
        ensureModerationStatus(status)
        val comment = loadComment(commentId, objectItemId)
        comment.status = status
        return objectItemCommentRepository.save(comment).toResponse()
    }

    @Transactional
    fun delete(
        objectItemId: Int,
        commentId: Int,
        request: ObjectItemManageVerifyRequest,
    ) {
        verifyProject(objectItemId, request)
        val comment = loadComment(commentId, objectItemId)
        comment.status = ObjectItemCommentStatus.DELETED
        objectItemCommentRepository.save(comment)
    }

    private fun verifyProject(objectItemId: Int, request: ObjectItemManageVerifyRequest) {
        objectItemManagementService.verify(objectItemId, request)
    }

    private fun ensureModerationStatus(status: ObjectItemCommentStatus) {
        if (status !in MODERATION_STATUSES) {
            throw ParamErrorException("评论审核状态只能是 APPROVED、REJECTED 或 DELETED")
        }
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

    private fun ObjectItemCommentManageStatusRequest.toVerifyRequest() =
        ObjectItemManageVerifyRequest(controlPassword = controlPassword)

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
        private val MODERATION_STATUSES = setOf(
            ObjectItemCommentStatus.APPROVED,
            ObjectItemCommentStatus.REJECTED,
            ObjectItemCommentStatus.DELETED,
        )
    }
}
