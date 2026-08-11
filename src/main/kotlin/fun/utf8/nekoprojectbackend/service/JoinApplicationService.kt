package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceConflictException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class JoinApplicationSaveRequest(
    @field:NotBlank(message = "申请人昵称不能为空")
    @field:Size(max = 64, message = "申请人昵称不能超过 64 个字符")
    val nickName: String = "",
    @field:NotBlank(message = "申请人 Minecraft ID 不能为空")
    @field:Size(max = 64, message = "申请人 Minecraft ID 不能超过 64 个字符")
    val mcId: String = "",
    @field:NotBlank(message = "申请人联系方式不能为空")
    @field:Size(max = 255, message = "申请人联系方式不能超过 255 个字符")
    val contact: String = "",
    @field:NotBlank(message = "申请理由不能为空")
    @field:Size(max = 4000, message = "申请理由不能超过 4000 个字符")
    val reason: String = "",
    @field:Size(max = 64, message = "申请岗位不能超过 64 个字符")
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

/** 加入申请业务：用户提交入组申请并校验昵称/MC ID/联系方式/理由等字段。 */
@Service
class JoinApplicationService(
    private val objectItemService: ObjectItemService,
    private val joinApplicationRepository: JoinApplicationRepository,
    private val submissionTrackingService: SubmissionTrackingService,
) {

    @Transactional
    fun create(objectItemId: Int, request: JoinApplicationSaveRequest): JoinApplicationResponse {
        return createEntity(objectItemId, request, trackingTokenHash = null).toResponse()
    }

    @Transactional
    fun createTracked(
        objectItemId: Int,
        request: JoinApplicationSaveRequest,
    ): TrackedSubmission<JoinApplicationResponse> {
        val issued = submissionTrackingService.issue()
        val entity = createEntity(objectItemId, request, issued.hash)
        return TrackedSubmission(
            value = entity.toResponse(),
            trackingToken = issued.token,
        )
    }

    @Transactional(readOnly = true)
    fun findTracked(objectItemId: Int, applicationId: Int, trackingToken: String): JoinApplicationResponse {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        if (applicationId <= 0) {
            throw ParamErrorException("加入申请 ID 必须大于 0")
        }
        val application = joinApplicationRepository.findById(applicationId)
            .orElseThrow { ResourceNotFoundException("加入申请不存在或追踪码不正确") }
        if (application.objectItemId != resolvedItemId ||
            !submissionTrackingService.matches(trackingToken, application.trackingTokenHash)
        ) {
            throw ResourceNotFoundException("加入申请不存在或追踪码不正确")
        }
        return application.toResponse()
    }

    private fun createEntity(
        objectItemId: Int,
        request: JoinApplicationSaveRequest,
        trackingTokenHash: String?,
    ): JoinApplication {
        val resolvedItemId = requirePositiveItemId(objectItemId)
        val selectedSkill = resolveRecruitmentSkill(resolvedItemId, request.skill)

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
            it.reason = requireText(
                request.reason,
                "申请理由不能为空",
                MAX_REASON_LENGTH,
                "申请理由不能超过 $MAX_REASON_LENGTH 个字符",
            )
            it.skill = selectedSkill
            it.status = JoinApplicationStatus.PENDING
            it.trackingTokenHash = trackingTokenHash
        }
        return joinApplicationRepository.save(entity)
    }

    private fun resolveRecruitmentSkill(objectItemId: Int, requestedSkill: String?): String {
        val project = objectItemService.findPublicById(objectItemId)
        if (project.status != ObjectItemStatus.RECRUITING) {
            throw ResourceConflictException("项目当前未开放招募")
        }
        val skill = normalizeNullableText(
            requestedSkill,
            MAX_SKILL_LENGTH,
            "申请岗位不能超过 $MAX_SKILL_LENGTH 个字符",
        ) ?: throw ParamErrorException("请选择申请岗位")
        val need = project.needMembers.firstOrNull {
            (it.number ?: 0) > 0 && it.skill?.trim()?.equals(skill, ignoreCase = true) == true
        } ?: throw ParamErrorException("申请岗位不在项目当前招募需求中")
        return need.skill?.trim().orEmpty()
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
        private const val MAX_REASON_LENGTH = 4_000
        private const val MAX_SKILL_LENGTH = 64
    }
}
