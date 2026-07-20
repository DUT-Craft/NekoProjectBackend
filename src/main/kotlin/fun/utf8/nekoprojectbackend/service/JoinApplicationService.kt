package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
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
    val applicantUserId: Long? = null,
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

/** 加入申请业务：用户提交入组申请并校验昵称/MC ID/联系方式/理由等字段。 */
@Service
class JoinApplicationService(
    private val objectItemRepository: ObjectItemRepository,
    private val joinApplicationRepository: JoinApplicationRepository,
) {

    @Transactional
    fun create(
        objectItemId: Int,
        request: JoinApplicationSaveRequest,
        applicantUserId: Long? = null,
    ): JoinApplicationResponse {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        ensureObjectItemExists(resolvedItemId)

        // 同一用户对同一项目已有待处理申请则拒绝重复（设计 §9.1）
        if (applicantUserId != null) {
            joinApplicationRepository
                .findByObjectItemIdAndStatus(resolvedItemId, JoinApplicationStatus.PENDING)
                .firstOrNull { it.applicantUserId == applicantUserId }
                ?.let { throw ParamErrorException("你已有一个待处理的申请") }
        }

        val entity = JoinApplication().also {
            it.objectItemId = resolvedItemId
            it.applicantUserId = applicantUserId
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

    /** 申请人撤回自己的待处理申请（设计 §9.1，状态置 WITHDRAWN）。 */
    @Transactional
    fun withdraw(objectItemId: Int, applicationId: Int, applicantUserId: Long): JoinApplicationResponse {
        val application = joinApplicationRepository.findById(applicationId)
            .orElseThrow { ResourceNotFoundException("加入申请不存在") }
        if (application.objectItemId != objectItemId) {
            throw ResourceNotFoundException("加入申请不存在")
        }
        if (application.applicantUserId != applicantUserId) {
            throw ForbiddenException("只能撤回自己的申请")
        }
        if (application.status != JoinApplicationStatus.PENDING) {
            throw ParamErrorException("只能撤回待处理的申请")
        }
        application.status = JoinApplicationStatus.WITHDRAWN
        return joinApplicationRepository.save(application).toResponse()
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
            applicantUserId = applicantUserId,
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
