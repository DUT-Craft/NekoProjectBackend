package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.ModerationProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class ObjectItemCommentSaveRequest(
    val nickName: String = "",
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

/** 项目评论业务：公开评论的创建与查询（查询默认仅返回已通过）。 */
@Service
class ObjectItemCommentService(
    private val objectItemRepository: ObjectItemRepository,
    private val objectItemCommentRepository: ObjectItemCommentRepository,
    private val moderationProperties: ModerationProperties,
) {

    @Transactional(readOnly = true)
    fun findByObjectItem(
        objectItemId: Int,
        status: ObjectItemCommentStatus?
    ): List<ObjectItemCommentResponse> {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        val effectiveStatus = status ?: ObjectItemCommentStatus.APPROVED
        ensureObjectItemExists(resolvedItemId)

        return objectItemCommentRepository.findByObjectItemIdAndStatus(resolvedItemId, effectiveStatus)
            .asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional(readOnly = true)
    fun findApproved(): List<ObjectItemCommentResponse> {
        return objectItemCommentRepository.findByStatus(ObjectItemCommentStatus.APPROVED)
            .asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional
    fun create(objectItemId: Int, request: ObjectItemCommentSaveRequest): ObjectItemCommentResponse {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        ensureObjectItemExists(resolvedItemId)

        val entity = ObjectItemComment().also {
            it.objectItemId = resolvedItemId
            it.nickName = requireText(
                request.nickName,
                "评论者昵称不能为空",
                MAX_NICK_NAME_LENGTH,
                "评论者昵称不能超过 $MAX_NICK_NAME_LENGTH 个字符",
            )
            it.content = requireText(request.content, "评论内容不能为空")
            it.status = if (moderationProperties.enabled) ObjectItemCommentStatus.PENDING else ObjectItemCommentStatus.APPROVED
        }
        return objectItemCommentRepository.save(entity).toResponse()
    }

    private fun ensureObjectItemExists(objectItemId: Int) {
        if (!objectItemRepository.existsById(objectItemId)) {
            throw ResourceNotFoundException("项目条目不存在")
        }
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
    }
}
