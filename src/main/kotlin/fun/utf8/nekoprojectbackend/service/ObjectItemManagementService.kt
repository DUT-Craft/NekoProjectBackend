package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ObjectItemManageVerifyRequest(
    @field:NotBlank(message = "项目控制密码不能为空")
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
)

data class ObjectItemManageUpdateRequest(
    @field:NotBlank(message = "项目控制密码不能为空")
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    @field:Size(max = 128, message = "项目标题不能超过 128 个字符")
    val title: String? = null,
    @field:Size(max = 64, message = "项目类型不能超过 64 个字符")
    val type: String? = null,
    @field:Size(max = 255, message = "项目简介不能超过 255 个字符")
    val introduction: String? = null,
    @field:Size(max = 20_000, message = "项目描述不能超过 20000 个字符")
    val description: String? = null,
    val status: ObjectItemStatus? = null,
    @field:Size(max = 64, message = "项目负责人不能超过 64 个字符")
    val leader: String? = null,
    @field:Size(max = 100, message = "项目招募需求不能超过 100 条")
    @field:Valid
    val needMembers: List<NeedMemberItemRequest>? = null,
    @field:Size(max = 12, message = "项目标签不能超过 12 个")
    val tags: List<String>? = null,
    @field:Size(max = 64, message = "负责人 Minecraft ID 不能超过 64 个字符")
    val leaderMcId: String? = null,
    @field:Size(max = 255, message = "联系方式不能超过 255 个字符")
    val contactInformation: String? = null,
    @field:Size(max = 512, message = "封面图地址不能超过 512 个字符")
    val coverImageUrl: String? = null,
    @field:Min(value = 0, message = "项目进度不能小于 0")
    @field:Max(value = 100, message = "项目进度不能大于 100")
    val progress: Int? = null,
)

data class ObjectItemPasswordChangeRequest(
    @field:NotBlank(message = "项目控制密码不能为空")
    @field:Size(max = 72, message = "项目控制密码不能超过 72 个字符")
    val controlPassword: String = "",
    @field:NotBlank(message = "新控制密码不能为空")
    @field:Size(min = 6, max = 72, message = "新控制密码长度必须为 6 到 72 个字符")
    val newControlPassword: String? = null,
)

/**
 * 项目条目管理业务：基于「项目控制密码」鉴权后执行更新/改密/删除。
 * 存储的密码可能是 BCrypt 哈希（新）或历史明文（旧），见 [matchesPassword]。
 */
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
        val item = loadAndVerify(id, request.controlPassword)
        ensureOwnerEditableStatus(item.status, request.status)
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
                coverImageUrl = request.coverImageUrl,
                progress = request.progress,
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

    /** 校验控制密码：BCrypt 哈希走 [org.springframework.security.crypto.password.PasswordEncoder.matches]；历史明文走 [constantTimeEquals] 常量时间比较。 */
    private fun matchesPassword(rawPassword: String?, storedPassword: String?): Boolean {
        if (rawPassword.isNullOrBlank() || storedPassword.isNullOrBlank()) {
            return false
        }
        if (rawPassword.toByteArray(Charsets.UTF_8).size > MAX_BCRYPT_PASSWORD_BYTES) {
            return false
        }
        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            return passwordEncoder.matches(rawPassword, storedPassword)
        }
        return constantTimeEquals(rawPassword, storedPassword)
    }

    private fun requireNewPassword(newPassword: String?): String {
        return ProjectControlPasswordPolicy.normalizeRequired(newPassword)
    }

    private fun requirePositiveId(id: Int): Int {
        if (id <= 0) {
            throw ParamErrorException("项目条目 ID 必须大于 0")
        }
        return id
    }

    /** 项目方只能在已通过审核的项目上维护运营阶段。 */
    private fun ensureOwnerEditableStatus(currentStatus: ObjectItemStatus?, targetStatus: ObjectItemStatus?) {
        if (targetStatus == null) return
        if (targetStatus !in OWNER_EDITABLE_STATUSES) {
            throw ForbiddenException("项目方只能修改运营状态：筹备中、招募中、进行中或已暂停")
        }
        if (currentStatus !in OWNER_EDITABLE_SOURCE_STATUSES) {
            throw ForbiddenException("项目尚未通过审核，不能进入运营状态")
        }
    }

    /** 常量时间字符串比较，避免明文密码校验时的时序侧信道。 */
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
            coverImageUrl = coverImageUrl,
            progress = progress,
            ownerId = ownerId,
            hasControlPassword = !controlPassword.isNullOrBlank(),
        )
    }

    private companion object {
        private const val BCRYPT_PREFIX = "\$2"
        private const val MAX_BCRYPT_PASSWORD_BYTES = 72
        private val OWNER_EDITABLE_STATUSES = setOf(
            ObjectItemStatus.PREPARING,
            ObjectItemStatus.RECRUITING,
            ObjectItemStatus.IN_PROGRESS,
            ObjectItemStatus.PAUSED,
        )
        private val OWNER_EDITABLE_SOURCE_STATUSES = OWNER_EDITABLE_STATUSES + ObjectItemStatus.APPROVED
    }
}
