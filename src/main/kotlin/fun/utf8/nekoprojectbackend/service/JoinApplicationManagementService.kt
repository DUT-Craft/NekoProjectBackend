package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceConflictException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class JoinApplicationRejectRequest(
    @field:NotBlank(message = "项目控制密码不能为空")
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    @field:Size(max = 255, message = "拒绝理由不能超过 255 个字符")
    val rejectReason: String? = null,
)

/** 管理员拒绝加入申请请求体：JWT 鉴权下无需项目控制密码，仅携带可选拒绝理由。 */
data class JoinApplicationAdminRejectRequest(
    @field:Size(max = 255, message = "拒绝理由不能超过 255 个字符")
    val rejectReason: String? = null,
)

data class JoinApplicationPageVO(
    val content: List<JoinApplicationResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
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
        val page = listByAdminPage(objectItemId, status, 0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("加入申请超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun listByAdminPage(
        objectItemId: Int,
        status: JoinApplicationStatus?,
        page: Int,
        size: Int,
    ): JoinApplicationPageVO {
        requirePositiveItemId(objectItemId)
        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                if (status != null) {
                    joinApplicationRepository.findByObjectItemIdAndStatus(objectItemId, status, pageable)
                } else {
                    joinApplicationRepository.findByObjectItemId(objectItemId, pageable)
                }
            },
            count = {
                if (status != null) {
                    joinApplicationRepository.countByObjectItemIdAndStatus(objectItemId, status)
                } else {
                    joinApplicationRepository.countByObjectItemId(objectItemId)
                }
            },
        )
    }

    private fun queryPage(
        page: Int,
        size: Int,
        query: (Pageable) -> Page<JoinApplication>,
        count: () -> Long,
    ): JoinApplicationPageVO {
        validatePageRequest(page, size)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = count()
            return JoinApplicationPageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }

        val result = query(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
        return JoinApplicationPageVO(
            content = result.content.map { it.toResponse() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = page,
            size = size,
        )
    }

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0) throw ParamErrorException("页码不能小于 0")
        if (size <= 0) throw ParamErrorException("每页条数必须大于 0")
        if (size > MAX_PAGE_SIZE) throw ParamErrorException("每页条数不能超过 $MAX_PAGE_SIZE 条")
    }

    private fun totalPages(totalElements: Long, size: Int): Int =
        if (totalElements == 0L) 0 else (((totalElements - 1) / size) + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun requirePositiveItemId(objectItemId: Int) {
        if (objectItemId <= 0) throw ParamErrorException("项目条目 ID 必须大于 0")
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
        val application = loadApplicationForUpdate(applicationId, objectItemId)
        ensureProcessable(application)
        application.status = JoinApplicationStatus.ACCEPTED
        application.rejectReason = null
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
        val application = loadApplicationForUpdate(applicationId, objectItemId)
        ensureProcessable(application)
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

    private fun loadApplicationForUpdate(applicationId: Int, objectItemId: Int): JoinApplication {
        if (applicationId <= 0) {
            throw ParamErrorException("加入申请 ID 必须大于 0")
        }
        val application = joinApplicationRepository.findByIdForUpdate(applicationId)
            ?: throw ResourceNotFoundException("加入申请不存在")
        if (application.objectItemId != objectItemId) {
            throw ResourceNotFoundException("加入申请不存在")
        }
        return application
    }

    private fun ensureProcessable(application: JoinApplication) {
        if (application.status !in PROCESSABLE_STATUSES) {
            throw ResourceConflictException("加入申请已处理，不能重复修改结果")
        }
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
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
        private val PROCESSABLE_STATUSES = setOf(
            JoinApplicationStatus.PENDING,
            JoinApplicationStatus.CONTACTED,
        )
    }
}
