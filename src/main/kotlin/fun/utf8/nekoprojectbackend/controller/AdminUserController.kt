package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.UserService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 管理端用户接口（/api/admin/users）：JWT 鉴权。
 *
 * - 用户创建仅限用户名为 `admin` 的账户调用（用于注册总管理等高权限账号），其余已登录用户一律 403；
 * - 项目管理列表仅供总管理在分配项目时下拉选择。
 *
 * 本项目不使用角色/权限体系，故在控制器内按用户名 / 角色（[AccessService]）软判定。
 */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val userService: UserService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    data class CreateUserRequest(
        val username: String,
        val password: String,
        val email: String,
        val role: Role = Role.PROJECT_MANAGER,
    )

    @PostMapping
    fun create(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody req: CreateUserRequest,
    ): ResponseEntity<Response> {
        if (admin.username != ADMIN_USERNAME) {
            throw ForbiddenException("仅管理员（admin）可创建用户")
        }
        val user = userService.createUser(req.username, req.password, req.email, req.role)
        operationLogService.record(
            operator = admin,
            action = "USER_CREATE",
            targetType = "USER",
            targetId = user.id,
            description = "创建用户 ${user.username}（${req.role}）",
        )

        data class UserCreatedResult(
            val id: Long?,
            val username: String,
            val email: String,
            val nickname: String,
            val role: Role,
            val status: Status,
        )

        val rs = UserCreatedResult(
            id = user.id,
            username = user.username,
            email = user.email,
            nickname = user.nickname,
            role = user.role ?: Role.PROJECT_MANAGER,
            status = user.status ?: Status.ACTIVE,
        )
        return builder.ok().data(rs).build()
    }

    /** 总管理分配项目时下拉用：返回全部项目管理账号（仅 id / 用户名 / 昵称）。仅总管理可调用。 */
    @GetMapping("/managers")
    fun listManagers(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val managers = userService.findByRole(Role.PROJECT_MANAGER)

        data class ManagerSummary(
            val id: Long,
            val username: String,
            val nickname: String,
        )

        val rs = managers.map {
            ManagerSummary(id = it.id!!, username = it.username, nickname = it.nickname)
        }
        return builder.ok().data(rs).build()
    }

    private companion object {
        const val ADMIN_USERNAME = "admin"
    }
}
