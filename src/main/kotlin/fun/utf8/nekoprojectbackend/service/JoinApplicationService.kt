package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class JoinApplicationSaveRequest(
    val nickName: String = "",
    val mcId: String = "",
    val contact: String = "",
    val reason: String = "",
    val skill: String? = null,
)

data class JoinApplicationResponse(
    val id: Int?,
    val objectItemId: Int?,
    val nickName: String?,
    val mcId: String?,
    val contact: String?,
    val reason: String?,
    val skill: String?,
    val status: JoinApplicationStatus?,
    val rejectReason: String? = null,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

@Service
class JoinApplicationService(
    private val objectItemRepository: ObjectItemRepository,
    private val joinApplicationRepository: JoinApplicationRepository,
) {

    @Transactional
    fun create(objectItemId: Int, request: JoinApplicationSaveRequest): JoinApplicationResponse {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        ensureObjectItemExists(resolvedItemId)

        val entity = JoinApplication().also {
            it.objectItemId = resolvedItemId
            it.nickName = requireText(
                request.nickName,
                "申请人昵称不能为空",
                MAX_NICK_NAME_LENGTH,
                "申请人昵称不能超过 $MAX_NICK_NAME_LENGTH 个字符"
            )
            it.mcId = requireText(
                request.mcId,
                "申请人 Minecraft ID 不能为空",
                MAX_MC_ID_LENGTH,
                "申请人 Minecraft ID 不能超过 $MAX_MC_ID_LENGTH 个字符"
            )
            it.contact = requireText(
                request.contact,
                "申请人联系方式不能为空",
                MAX_CONTACT_LENGTH,
                "申请人联系方式不能超过 $MAX_CONTACT_LENGTH 个字符"
            )
            it.reason = requireText(request.reason, "申请理由不能为空")
            it.skill =
                normalizeNullableText(request.skill, MAX_SKILL_LENGTH, "申请岗位不能超过 $MAX_SKILL_LENGTH 个字符")
            it.status = JoinApplicationStatus.PENDING
        }
        return joinApplicationRepository.save(entity).toResponse()
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

    private fun normalizeNullableText(value: String?): String? {
        return value?.trim()?.ifBlank { null }
    }

    private fun normalizeNullableText(value: String?, maxLength: Int, tooLongMessage: String): String? {
        val normalized = normalizeNullableText(value)
        if (normalized != null && normalized.length > maxLength) {
            throw ParamErrorException(tooLongMessage)
        }
        return normalized
    }

    private fun JoinApplication.toResponse(): JoinApplicationResponse {
        return JoinApplicationResponse(
            id = id,
            objectItemId = objectItemId,
            nickName = nickName,
            mcId = mcId,
            contact = contact,
            reason = reason,
            skill = skill,
            status = status,
            rejectReason = rejectReason,
            createTime = createTime,
            updateTime = updateTime,
        )
    }

    private companion object {
        private const val MAX_NICK_NAME_LENGTH = 64
        private const val MAX_MC_ID_LENGTH = 64
        private const val MAX_CONTACT_LENGTH = 255
        private const val MAX_SKILL_LENGTH = 64
    }
}
