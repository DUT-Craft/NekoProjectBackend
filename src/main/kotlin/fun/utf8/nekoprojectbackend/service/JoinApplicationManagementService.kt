package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 管理员拒绝加入申请请求体：JWT 鉴权，仅携带可选拒绝理由。 */
data class JoinApplicationAdminRejectRequest(
    val rejectReason: String? = null,
)

/**
 * 加入申请管理业务：统一走 JWT 鉴权（项目 OWNER/MANAGER 或超管，由 AccessService.ensureCanManage 校验）。
 * 接受申请时同事务创建 ACTIVE MEMBER（设计 §9.1）。
 */
@Service
class JoinApplicationManagementService(
    private val joinApplicationRepository: JoinApplicationRepository,
    private val projectMemberService: ProjectMemberService,
) {

    @Transactional(readOnly = true)
    fun listByAdmin(
        objectItemId: Int,
        status: JoinApplicationStatus?,
    ): List<JoinApplicationResponse> {
        val applications = if (status != null) {
            joinApplicationRepository.findByObjectItemIdAndStatus(objectItemId, status)
        } else {
            joinApplicationRepository.findByObjectItemId(objectItemId)
        }
        return applications.asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    /** 同意加入申请：JWT 鉴权（由控制器层保证），同事务创建 ACTIVE MEMBER（设计 §9.1）。 */
    @Transactional
    fun acceptByAdmin(
        objectItemId: Int,
        applicationId: Int,
    ): JoinApplicationResponse {
        val application = loadApplication(applicationId, objectItemId)
        application.status = JoinApplicationStatus.ACCEPTED
        val saved = joinApplicationRepository.save(application)
        // 同事务创建成员关系；匿名历史申请（applicantUserId=null）跳过
        saved.applicantUserId?.let { projectMemberService.upsertMemberOnAccept(objectItemId, it) }
        return saved.toResponse()
    }

    /** 拒绝加入申请：JWT 鉴权。 */
    @Transactional
    fun rejectByAdmin(
        objectItemId: Int,
        applicationId: Int,
        rejectReason: String?,
    ): JoinApplicationResponse {
        val application = loadApplication(applicationId, objectItemId)
        application.status = JoinApplicationStatus.REJECTED
        application.rejectReason = normalizeRejectReason(rejectReason)
        return joinApplicationRepository.save(application).toResponse()
    }

    private fun normalizeRejectReason(rejectReason: String?): String? {
        val normalized = rejectReason?.trim()?.ifBlank { null }
        if (normalized != null && normalized.length > MAX_REJECT_REASON_LENGTH) {
            throw ParamErrorException("拒绝理由不能超过 $MAX_REJECT_REASON_LENGTH 个字符")
        }
        return normalized
    }

    private fun loadApplication(applicationId: Int, objectItemId: Int): JoinApplication {
        if (applicationId <= 0) {
            throw ParamErrorException("加入申请 ID 必须大于 0")
        }
        val application = joinApplicationRepository.findById(applicationId)
            .orElseThrow { ResourceNotFoundException("加入申请不存在") }
        if (application.objectItemId != objectItemId) {
            throw ResourceNotFoundException("加入申请不存在")
        }
        return application
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
        private const val MAX_REJECT_REASON_LENGTH = 255
    }
}
