package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
}
