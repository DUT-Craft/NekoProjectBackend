package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdate
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemUpdateManageCreateRequest(
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    @field:NotBlank(message = "动态标题不能为空")
    @field:Size(max = 128, message = "动态标题不能超过 128 个字符")
    val title: String = "",
    @field:NotBlank(message = "动态内容不能为空")
    @field:Size(max = 10_000, message = "动态内容不能超过 10000 个字符")
    val content: String = "",
    @field:Size(max = 512, message = "动态图片 URL 不能超过 512 个字符")
    val imageUrl: String? = null,
    val status: ObjectItemUpdateStatus? = ObjectItemUpdateStatus.PENDING,
)

data class ObjectItemUpdateManageUpdateRequest(
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    @field:Size(max = 128, message = "动态标题不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 10_000, message = "动态内容不能超过 10000 个字符")
    val content: String? = null,
    @field:Size(max = 512, message = "动态图片 URL 不能超过 512 个字符")
    val imageUrl: String? = null,
    val status: ObjectItemUpdateStatus? = null,
)

/** 项目动态管理业务：凭项目控制密码创建/更新/删除动态，或管理员直接审核状态。 */
@Service
class ObjectItemUpdateManagementService(
    private val objectItemManagementService: ObjectItemManagementService,
    private val objectItemUpdateRepository: ObjectItemUpdateRepository,
) {

    @Transactional(readOnly = true)
    fun list(
        objectItemId: Int,
        status: ObjectItemUpdateStatus?,
        request: ObjectItemManageVerifyRequest,
    ): List<ObjectItemUpdateResponse> {
        verifyProject(objectItemId, request)
        return listByAdmin(objectItemId, status)
    }

    /** 管理员查看项目动态：JWT 鉴权（由控制器层保证），无需项目控制密码。 */
    @Transactional(readOnly = true)
    fun listByAdmin(
        objectItemId: Int,
        status: ObjectItemUpdateStatus?,
    ): List<ObjectItemUpdateResponse> {
        val page = listByAdminPage(objectItemId, status, 0, MAX_UNPAGED_RESULTS)
        if (page.totalElements > MAX_UNPAGED_RESULTS) {
            throw ParamErrorException("项目动态超过 $MAX_UNPAGED_RESULTS 条，请使用分页查询")
        }
        return page.content
    }

    @Transactional(readOnly = true)
    fun listByAdminPage(
        objectItemId: Int,
        status: ObjectItemUpdateStatus?,
        page: Int,
        size: Int,
    ): ObjectItemUpdatePageVO {
        requirePositiveItemId(objectItemId)
        return queryPage(
            page = page,
            size = size,
            query = { pageable ->
                if (status != null) {
                    objectItemUpdateRepository.findByObjectItemIdAndStatus(objectItemId, status, pageable)
                } else {
                    objectItemUpdateRepository.findByObjectItemId(objectItemId, pageable)
                }
            },
            count = {
                if (status != null) {
                    objectItemUpdateRepository.countByObjectItemIdAndStatus(objectItemId, status)
                } else {
                    objectItemUpdateRepository.countByObjectItemId(objectItemId)
                }
            },
        )
    }

    private fun queryPage(
        page: Int,
        size: Int,
        query: (Pageable) -> Page<ObjectItemUpdate>,
        count: () -> Long,
    ): ObjectItemUpdatePageVO {
        validatePageRequest(page, size)
        if (page.toLong() * size > Int.MAX_VALUE) {
            val totalElements = count()
            return ObjectItemUpdatePageVO(
                content = emptyList(),
                totalElements = totalElements,
                totalPages = totalPages(totalElements, size),
                page = page,
                size = size,
            )
        }

        val result = query(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
        return ObjectItemUpdatePageVO(
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
    fun create(
        objectItemId: Int,
        request: ObjectItemUpdateManageCreateRequest,
    ): ObjectItemUpdateResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
        return createByAdmin(objectItemId, request.copy(status = ObjectItemUpdateStatus.APPROVED))
    }

    /** 管理员发布项目动态：JWT 鉴权，无需项目控制密码。 */
    @Transactional
    fun createByAdmin(
        objectItemId: Int,
        request: ObjectItemUpdateManageCreateRequest,
    ): ObjectItemUpdateResponse {
        val entity = ObjectItemUpdate().also {
            it.objectItemId = objectItemId
            it.title = requireText(
                request.title,
                "动态标题不能为空",
                MAX_TITLE_LENGTH,
                "动态标题不能超过 $MAX_TITLE_LENGTH 个字符",
            )
            it.content = requireText(
                request.content,
                "动态内容不能为空",
                MAX_CONTENT_LENGTH,
                "动态内容不能超过 $MAX_CONTENT_LENGTH 个字符",
            )
            it.imageUrl = ImageUrlPolicy.normalize(
                request.imageUrl,
                MAX_IMAGE_URL_LENGTH,
                "动态图片 URL",
            )
            it.status = ObjectItemUpdateStatus.APPROVED
        }
        return objectItemUpdateRepository.save(entity).toResponse()
    }

    @Transactional
    fun update(
        objectItemId: Int,
        updateId: Int,
        request: ObjectItemUpdateManageUpdateRequest,
    ): ObjectItemUpdateResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
        if (request.status != null) {
            throw ForbiddenException("项目方不能修改动态审核状态")
        }
        return updateByAdmin(objectItemId, updateId, request)
    }

    /** 管理员修改项目动态：JWT 鉴权，无需项目控制密码。空值字段表示不修改。 */
    @Transactional
    fun updateByAdmin(
        objectItemId: Int,
        updateId: Int,
        request: ObjectItemUpdateManageUpdateRequest,
    ): ObjectItemUpdateResponse {
        if (request.status != null) {
            throw ParamErrorException("动态审核状态请使用专用审核接口修改")
        }
        val update = loadUpdate(updateId, objectItemId)
        applyUpdateFields(update, request)
        return objectItemUpdateRepository.save(update).toResponse()
    }

    @Transactional
    fun reviewByAdmin(
        objectItemId: Int,
        updateId: Int,
        status: ObjectItemUpdateStatus,
    ): ObjectItemUpdateResponse {
        ensureModerationStatus(status)
        val update = loadUpdate(updateId, objectItemId)
        update.status = status
        return objectItemUpdateRepository.save(update).toResponse()
    }

    @Transactional
    fun delete(
        objectItemId: Int,
        updateId: Int,
        request: ObjectItemManageVerifyRequest,
    ) {
        verifyProject(objectItemId, request)
        deleteByAdmin(objectItemId, updateId)
    }

    /** 管理员删除项目动态（软删除置 DELETED）：JWT 鉴权，无需项目控制密码。 */
    @Transactional
    fun deleteByAdmin(
        objectItemId: Int,
        updateId: Int,
    ) {
        val update = loadUpdate(updateId, objectItemId)
        update.status = ObjectItemUpdateStatus.DELETED
        objectItemUpdateRepository.save(update)
    }

    /** 把编辑请求的非空字段应用到动态实体，供项目方 / 管理员更新复用。 */
    private fun applyUpdateFields(update: ObjectItemUpdate, request: ObjectItemUpdateManageUpdateRequest) {
        request.title?.let {
            update.title = requireText(
                it,
                "动态标题不能为空",
                MAX_TITLE_LENGTH,
                "动态标题不能超过 $MAX_TITLE_LENGTH 个字符",
            )
        }
        request.content?.let {
            update.content = requireText(
                it,
                "动态内容不能为空",
                MAX_CONTENT_LENGTH,
                "动态内容不能超过 $MAX_CONTENT_LENGTH 个字符",
            )
        }
        request.imageUrl?.let {
            update.imageUrl = ImageUrlPolicy.normalize(
                it,
                MAX_IMAGE_URL_LENGTH,
                "动态图片 URL",
            )
        }
    }

    private fun verifyProject(objectItemId: Int, request: ObjectItemManageVerifyRequest) {
        objectItemManagementService.verify(objectItemId, request)
    }

    private fun ensureModerationStatus(status: ObjectItemUpdateStatus) {
        if (status !in MODERATION_STATUSES) {
            throw ParamErrorException("动态审核状态只能是 APPROVED、REJECTED 或 DELETED")
        }
    }

    private fun loadUpdate(updateId: Int, objectItemId: Int): ObjectItemUpdate {
        if (updateId <= 0) {
            throw ParamErrorException("项目动态 ID 必须大于 0")
        }
        val update = objectItemUpdateRepository.findById(updateId)
            .orElseThrow { ResourceNotFoundException("项目动态不存在") }
        if (update.objectItemId != objectItemId) {
            throw ResourceNotFoundException("项目动态不存在")
        }
        return update
    }

    private fun ObjectItemUpdateManageCreateRequest.toVerifyRequest() =
        ObjectItemManageVerifyRequest(controlPassword = controlPassword)

    private fun ObjectItemUpdateManageUpdateRequest.toVerifyRequest() =
        ObjectItemManageVerifyRequest(controlPassword = controlPassword)

    private fun requireText(value: String, blankMessage: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw ParamErrorException(blankMessage)
        }
        return normalized
    }

    private fun requireText(
        value: String,
        blankMessage: String,
        maxLength: Int,
        tooLongMessage: String,
    ): String {
        val normalized = requireText(value, blankMessage)
        if (normalized.length > maxLength) {
            throw ParamErrorException(tooLongMessage)
        }
        return normalized
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

    private companion object {
        private const val MAX_TITLE_LENGTH = 128
        private const val MAX_CONTENT_LENGTH = 10_000
        private const val MAX_IMAGE_URL_LENGTH = 512
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
        private val MODERATION_STATUSES = setOf(
            ObjectItemUpdateStatus.APPROVED,
            ObjectItemUpdateStatus.REJECTED,
            ObjectItemUpdateStatus.DELETED,
        )
    }
}
