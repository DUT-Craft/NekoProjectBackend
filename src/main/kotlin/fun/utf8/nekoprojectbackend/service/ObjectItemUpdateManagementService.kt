package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdate
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemUpdateManageCreateRequest(
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val status: ObjectItemUpdateStatus? = ObjectItemUpdateStatus.PENDING,
)

data class ObjectItemUpdateManageUpdateRequest(
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val status: ObjectItemUpdateStatus? = null,
)

/** 项目动态管理业务：统一 JWT 鉴权（项目 OWNER/MANAGER 或超管，由 AccessService.ensureCanManage 校验）。 */
@Service
class ObjectItemUpdateManagementService(
    private val objectItemUpdateRepository: ObjectItemUpdateRepository,
) {

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
            it.content = requireText(request.content, "动态内容不能为空")
            it.imageUrl = normalizeNullableText(
                request.imageUrl,
                MAX_IMAGE_URL_LENGTH,
                "动态图片 URL 不能超过 $MAX_IMAGE_URL_LENGTH 个字符",
            )
            it.status = request.status ?: ObjectItemUpdateStatus.PENDING
        }
        return objectItemUpdateRepository.save(entity).toResponse()
    }

    @Transactional
    fun updateByAdmin(
        objectItemId: Int,
        updateId: Int,
        request: ObjectItemUpdateManageUpdateRequest,
    ): ObjectItemUpdateResponse {
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
        val update = loadUpdate(updateId, objectItemId)
        update.status = status
        return objectItemUpdateRepository.save(update).toResponse()
    }

    /** 删除项目动态（软删除置 DELETED）：JWT 鉴权。 */
    @Transactional
    fun deleteByAdmin(
        objectItemId: Int,
        updateId: Int,
    ) {
        val update = loadUpdate(updateId, objectItemId)
        update.status = ObjectItemUpdateStatus.DELETED
        objectItemUpdateRepository.save(update)
    }

    /** 把编辑请求的非空字段应用到动态实体。 */
    private fun applyUpdateFields(update: ObjectItemUpdate, request: ObjectItemUpdateManageUpdateRequest) {
        request.title?.let {
            update.title = requireText(
                it,
                "动态标题不能为空",
                MAX_TITLE_LENGTH,
                "动态标题不能超过 $MAX_TITLE_LENGTH 个字符",
            )
        }
        request.content?.let { update.content = requireText(it, "动态内容不能为空") }
        request.imageUrl?.let {
            update.imageUrl = normalizeNullableText(
                it,
                MAX_IMAGE_URL_LENGTH,
                "动态图片 URL 不能超过 $MAX_IMAGE_URL_LENGTH 个字符",
            )
        }
        request.status?.let { update.status = it }
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
        private const val MAX_IMAGE_URL_LENGTH = 512
        private const val MAX_UNPAGED_RESULTS = 500
        private const val MAX_PAGE_SIZE = 500
    }
}
