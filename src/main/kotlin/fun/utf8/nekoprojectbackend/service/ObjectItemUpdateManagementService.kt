package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdate
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemUpdateManageCreateRequest(
    val controlPassword: String = "",
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val status: ObjectItemUpdateStatus? = ObjectItemUpdateStatus.PENDING,
)

data class ObjectItemUpdateManageUpdateRequest(
    val controlPassword: String = "",
    val title: String? = null,
    val content: String? = null,
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
        val updates = if (status != null) {
            objectItemUpdateRepository.findByObjectItemIdAndStatus(objectItemId, status)
        } else {
            objectItemUpdateRepository.findByObjectItemId(objectItemId)
        }
        return updates.asSequence()
            .sortedBy { it.id ?: Int.MAX_VALUE }
            .map { it.toResponse() }
            .toList()
    }

    @Transactional
    fun create(
        objectItemId: Int,
        request: ObjectItemUpdateManageCreateRequest,
    ): ObjectItemUpdateResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
        return createByAdmin(objectItemId, request)
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
    fun update(
        objectItemId: Int,
        updateId: Int,
        request: ObjectItemUpdateManageUpdateRequest,
    ): ObjectItemUpdateResponse {
        verifyProject(objectItemId, request.toVerifyRequest())
        return updateByAdmin(objectItemId, updateId, request)
    }

    /** 管理员修改项目动态：JWT 鉴权，无需项目控制密码。空值字段表示不修改。 */
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

    private fun verifyProject(objectItemId: Int, request: ObjectItemManageVerifyRequest) {
        objectItemManagementService.verify(objectItemId, request)
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
    }
}
