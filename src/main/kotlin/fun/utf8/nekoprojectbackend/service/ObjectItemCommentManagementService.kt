package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemCommentManageStatusRequest(
    val controlPassword: String = "",
    val status: ObjectItemCommentStatus = ObjectItemCommentStatus.APPROVED,
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
        val comments = if (status != null) {
            objectItemCommentRepository.findByObjectItemIdAndStatus(objectItemId, status)
        } else {
            objectItemCommentRepository.findByObjectItemId(objectItemId)
        }
        return comments.asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional
    fun review(
        objectItemId: Int,
        commentId: Int,
        request: ObjectItemCommentManageStatusRequest,
    ): ObjectItemCommentResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
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
}
