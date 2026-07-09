package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemManageVerifyRequest(
    val controlPassword: String = "",
)

data class ObjectItemManageUpdateRequest(
    val controlPassword: String = "",
    val title: String? = null,
    val type: String? = null,
    val introduction: String? = null,
    val description: String? = null,
    val status: ObjectItemStatus? = null,
    val leader: String? = null,
    val needMembers: List<NeedMemberItemRequest>? = null,
    val tags: List<String>? = null,
    val leaderMcId: String? = null,
    val contactInformation: String? = null,
)

data class ObjectItemPasswordChangeRequest(
    val controlPassword: String = "",
    val newControlPassword: String? = null,
)

@Service
class ObjectItemManagementService(
    private val objectItemRepository: ObjectItemRepository,
    private val objectItemService: ObjectItemService,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional(readOnly = true)
    fun verify(id: Int, request: ObjectItemManageVerifyRequest): ObjectItemResponse {
        return loadAndVerify(id, request.controlPassword).toResponse()
    }

    @Transactional
    fun update(id: Int, request: ObjectItemManageUpdateRequest): ObjectItemResponse {
        loadAndVerify(id, request.controlPassword)
        return objectItemService.update(
            id,
            ObjectItemUpdateRequest(
                id = id,
                title = request.title,
                type = request.type,
                introduction = request.introduction,
                description = request.description,
                status = request.status,
                leader = request.leader,
                needMembers = request.needMembers,
                tags = request.tags,
                leaderMcId = request.leaderMcId,
                contactInformation = request.contactInformation,
            ),
        )
    }

    @Transactional
    fun changePassword(id: Int, request: ObjectItemPasswordChangeRequest): ObjectItemResponse {
        val item = loadAndVerify(id, request.controlPassword)
        item.controlPassword = passwordEncoder.encode(requireNewPassword(request.newControlPassword))
        return objectItemRepository.save(item).toResponse()
    }

    @Transactional
    fun delete(id: Int, request: ObjectItemManageVerifyRequest): ObjectItemResponse {
        loadAndVerify(id, request.controlPassword)
        return objectItemService.update(
            id,
            ObjectItemUpdateRequest(id = id, status = ObjectItemStatus.DELETED),
        )
    }

    private fun loadAndVerify(id: Int, controlPassword: String?): ObjectItem {
        val positiveId = requirePositiveId(id)
        val item = objectItemRepository.findById(positiveId)
            .orElseThrow { ResourceNotFoundException("项目条目不存在") }
        if (!matchesPassword(controlPassword, item.controlPassword)) {
            throw ForbiddenException("项目控制密码错误")
        }
        return item
    }

    private fun matchesPassword(rawPassword: String?, storedPassword: String?): Boolean {
        if (rawPassword.isNullOrBlank() || storedPassword.isNullOrBlank()) {
            return false
        }
        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            return passwordEncoder.matches(rawPassword, storedPassword)
        }
        return constantTimeEquals(rawPassword, storedPassword)
    }

    private fun requireNewPassword(newPassword: String?): String {
        val normalized = newPassword?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw ParamErrorException("新控制密码不能为空")
        }
        if (normalized.length > MAX_CONTROL_PASSWORD_LENGTH) {
            throw ParamErrorException("项目控制密码不能超过 $MAX_CONTROL_PASSWORD_LENGTH 个字符")
        }
        return normalized
    }

    private fun requirePositiveId(id: Int): Int {
        if (id <= 0) {
            throw ParamErrorException("项目条目 ID 必须大于 0")
        }
        return id
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun ObjectItem.toResponse(): ObjectItemResponse {
        return ObjectItemResponse(
            id = id,
            title = title,
            type = type,
            introduction = introduction,
            description = description,
            status = status,
            leader = leader,
            needMembers = needMembers.orEmpty().map {
                NeedMemberItemResponse(
                    skill = it.skill,
                    number = it.number,
                    context = it.context,
                )
            },
            tags = tags.orEmpty().toList(),
            leaderMcId = leaderMcId,
            contactInformation = contactInformation,
            hasControlPassword = !controlPassword.isNullOrBlank(),
        )
    }

    private companion object {
        private const val BCRYPT_PREFIX = "\$2"
        private const val MAX_CONTROL_PASSWORD_LENGTH = 255
    }
}
