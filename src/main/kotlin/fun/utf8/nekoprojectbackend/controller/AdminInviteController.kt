package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.InviteCodeStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.InviteCodeService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.UserService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 总管理邀请码接口（/api/admin/invites）：JWT 鉴权，仅总管理可调用。
 *
 * - POST 生成一次性邀请码（供项目管理通过 `/api/auth/register/manager` 注册消费）；
 * - GET 查看历史邀请码（状态：未使用 / 已使用 / 已过期，含创建者与消费者用户名）。
 */
@RestController
@RequestMapping("/api/admin/invites")
class AdminInviteController(
    private val inviteCodeService: InviteCodeService,
    private val accessService: AccessService,
    private val userService: UserService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    @PostMapping
    fun generate(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val invite = inviteCodeService.generate(admin.id)
        operationLogService.record(
            operator = admin,
            action = "INVITE_GENERATE",
            targetType = "INVITE",
            targetId = invite.code,
            description = "生成项目管理邀请码",
        )

        data class InviteCodeResult(
            val inviteCode: String,
        )
        return builder.ok().data(InviteCodeResult(inviteCode = invite.code)).build()
    }

    @GetMapping
    fun list(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val invites = inviteCodeService.list()
        // 批量解析创建者 / 消费者用户名，便于前端直接展示
        val userIds = invites.flatMap { listOfNotNull(it.createdBy, it.usedBy) }.distinct()
        val nameById = userService.namesByIds(userIds)

        data class InviteHistory(
            val code: String,
            val status: String,
            val createdById: Long,
            val createdBy: String,
            val createdAt: String,
            val usedById: Long?,
            val usedBy: String?,
            val usedAt: String?,
            val expiresAt: String,
        )

        val rs = invites.map {
            InviteHistory(
                code = it.code,
                status = (it.status ?: InviteCodeStatus.UNUSED).name,
                createdById = it.createdBy,
                createdBy = nameById[it.createdBy] ?: "未知",
                createdAt = it.createdAt.toString(),
                usedById = it.usedBy,
                usedBy = it.usedBy?.let { id -> nameById[id] },
                usedAt = it.usedAt?.toString(),
                expiresAt = it.expiresAt.toString(),
            )
        }
        return builder.ok().data(rs).build()
    }
}
