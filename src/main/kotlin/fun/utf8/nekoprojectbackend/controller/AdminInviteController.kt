package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.InviteCodeService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 总管理邀请码接口（/api/admin/invites）：JWT 鉴权，仅总管理可调用。
 *
 * 生成的邀请码为一次性，供项目管理通过 `/api/auth/register/manager` 注册消费。
 */
@RestController
@RequestMapping("/api/admin/invites")
class AdminInviteController(
    private val inviteCodeService: InviteCodeService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    @PostMapping
    fun generate(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val code = inviteCodeService.generate()
        operationLogService.record(
            operator = admin,
            action = "INVITE_GENERATE",
            targetType = "INVITE",
            targetId = code,
            description = "生成项目管理邀请码",
        )

        data class InviteCodeResult(
            val inviteCode: String,
        )
        return builder.ok().data(InviteCodeResult(inviteCode = code)).build()
    }
}
