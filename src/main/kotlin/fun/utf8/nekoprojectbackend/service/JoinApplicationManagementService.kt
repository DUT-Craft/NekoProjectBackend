package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class JoinApplicationRejectRequest(
    val controlPassword: String = "",
    val rejectReason: String? = null,
)

/** 管理员拒绝加入申请请求体：JWT 鉴权下无需项目控制密码，仅携带可选拒绝理由。 */
data class JoinApplicationAdminRejectRequest(
    val rejectReason: String? = null,
)

/** 加入申请管理业务：凭项目控制密码查看/接受/拒绝申请，或管理员（JWT）直接处理。 */
@Service
class JoinApplicationManagementService(
    private val objectItemManagementService: ObjectItemManagementService,
    private val joinApplicationRepository: JoinApplicationRepository,
) {

    @Transactional(readOnly = true)
    fun list(
        objectItemId: Int,
        status: JoinApplicationStatus?,
        request: ObjectItemManageVerifyRequest,
    ): List<JoinApplicationResponse> {
        verifyProject(objectItemId, request)
        return listByAdmin(objectItemId, status)
    }

    /** 管理员查看加入申请：JWT 鉴权（由控制器层保证），无需项目控制密码。 */
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

    @Transactional
    fun accept(
        objectItemId: Int,
        applicationId: Int,
        request: ObjectItemManageVerifyRequest,
    ): JoinApplicationResponse {
        verifyProject(objectItemId, request)
        return acceptByAdmin(objectItemId, applicationId)
    }

    /** 管理员同意加入申请：JWT 鉴权，无需项目控制密码。 */
    @Transactional
    fun acceptByAdmin(
        objectItemId: Int,
        applicationId: Int,
    ): JoinApplicationResponse {
        val application = loadApplication(applicationId, objectItemId)
        application.status = JoinApplicationStatus.ACCEPTED
        return joinApplicationRepository.save(application).toResponse()
    }

    @Transactional
    fun reject(
        objectItemId: Int,
        applicationId: Int,
        request: JoinApplicationRejectRequest,
    ): JoinApplicationResponse {
        verifyProject(objectItemId, ObjectItemManageVerifyRequest(request.controlPassword))
        return rejectByAdmin(objectItemId, applicationId, request.rejectReason)
    }

    /** 管理员拒绝加入申请：JWT 鉴权，无需项目控制密码。 */
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

    private fun verifyProject(objectItemId: Int, request: ObjectItemManageVerifyRequest) {
        objectItemManagementService.verify(objectItemId, request)
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
