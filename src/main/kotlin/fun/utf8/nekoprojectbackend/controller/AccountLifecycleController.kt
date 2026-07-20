package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccountLifecycleService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 账号生命周期自助接口（/api/user，设计 §10）：停用 / 注销。JWT 鉴权。
 *
 * 恢复停用账号不在此处：停用后原会话全部失效、无有效 token，无法走需 JWT 的接口。
 * 恢复链路改为匿名凭邮箱验证码 + 密码，见 [fun.utf8.nekoprojectbackend.controller.AuthController.reactivate]。
 */
@RestController
@RequestMapping("/api/user")
class AccountLifecycleController(
    private val accountLifecycleService: AccountLifecycleService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    data class DeleteAccountRequest(val confirmPassword: String)

    /** 用户自助停用账号（吊销全部会话，冷静期内可凭邮箱验证码恢复）。 */
    @PostMapping("/deactivate")
    fun deactivate(@AuthenticationPrincipal user: LoginUser): ResponseEntity<Response> {
        accountLifecycleService.deactivate(user.id)
        operationLogService.record(operator = user, action = "USER_DEACTIVATE", targetType = "USER", targetId = user.id, description = "账号停用")
        return builder.ok().message("账号已停用，可凭邮箱验证码恢复").build()
    }

    /** 用户注销账号（需二次确认密码 + 预检无拥有项目）。 */
    @PostMapping("/delete")
    fun delete(
        @AuthenticationPrincipal user: LoginUser,
        @RequestBody req: DeleteAccountRequest,
    ): ResponseEntity<Response> {
        accountLifecycleService.delete(user.id, req.confirmPassword)
        operationLogService.record(operator = user, action = "USER_DELETE", targetType = "USER", targetId = user.id, description = "账号注销")
        return builder.ok().message("账号已注销").build()
    }
}
