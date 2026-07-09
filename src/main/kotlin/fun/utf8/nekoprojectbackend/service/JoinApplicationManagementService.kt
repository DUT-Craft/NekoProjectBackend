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
        val application = loadApplication(applicationId, objectItemId)
        application.status = JoinApplicationStatus.REJECTED
        application.rejectReason = normalizeRejectReason(request.rejectReason)
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
