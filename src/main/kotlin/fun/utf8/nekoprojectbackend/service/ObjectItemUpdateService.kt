package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdate
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
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

/** 项目动态业务：动态查询（默认仅返回已通过）。 */
@Service
class ObjectItemUpdateService(
    private val objectItemRepository: ObjectItemRepository,
    private val objectItemUpdateRepository: ObjectItemUpdateRepository,
) {

    @Transactional(readOnly = true)
    fun findByObjectItem(objectItemId: Int, status: ObjectItemUpdateStatus?): List<ObjectItemUpdateResponse> {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        val effectiveStatus = status ?: ObjectItemUpdateStatus.APPROVED
        ensureObjectItemExists(resolvedItemId)

        return objectItemUpdateRepository.findByObjectItemIdAndStatus(resolvedItemId, effectiveStatus)
            .asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional(readOnly = true)
    fun findApproved(): List<ObjectItemUpdateResponse> {
        return objectItemUpdateRepository.findByStatus(ObjectItemUpdateStatus.APPROVED)
            .asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
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
}
