package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 管理员拒绝加入申请请求体：JWT 鉴权，仅携带可选拒绝理由。 */
data class JoinApplicationAdminRejectRequest(
    val rejectReason: String? = null,
)

data class JoinApplicationPageVO(
    val content: List<JoinApplicationResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
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
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
    }
}
