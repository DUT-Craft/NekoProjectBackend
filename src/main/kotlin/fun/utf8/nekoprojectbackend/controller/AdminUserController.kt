package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.UserNotFoundException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.TokenStore
import `fun`.utf8.nekoprojectbackend.service.UserService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

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
    private val tokenStore: TokenStore,
    private val passwordEncoder: PasswordEncoder,
    private val builder: ResponseBuilder,
) {

    data class CreateUserRequest(
        val username: String,
        val password: String,
        val email: String,
        val role: Role = Role.USER,
    )

    @PostMapping
    fun create(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody req: CreateUserRequest,
    ): ResponseEntity<Response> {
        // 仅总管理可创建用户（基于角色判定，而非用户名字符串——后者在 neko.admin.username 改名后失效）
        accessService.requireSuperAdmin(admin)
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
            role = user.role,
            status = user.status,
        )
        return builder.ok().data(rs).build()
    }

    /** 总管理分配项目时下拉用：返回可归属项目的全部账号（项目管理 + 总管理，含角色标签）。仅总管理可调用。 */
    @GetMapping("/managers")
    fun listManagers(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val owners = userService.findAssignableOwners()

        data class ManagerSummary(
            val id: Long,
            val username: String,
            val nickname: String,
            val role: Role,
        )

        val rs = owners.map {
            ManagerSummary(
                id = it.id!!,
                username = it.username,
                nickname = it.nickname,
                role = it.role,
            )
        }
        return builder.ok().data(rs).build()
    }

    data class GrantCreateProjectRequest(val reason: String? = null)

    data class BanRequest(
        val reason: String? = null,
        val endTime: LocalDateTime? = null,
        /** 敏感操作二次确认：当前管理员密码（设计 §6.4 / §11）。 */
        val confirmPassword: String = "",
    )

    /** 总管理封禁用户（设计 §10.1）：置 BANNED，立即吊销全部会话，埋审计。 */
    @PostMapping("/{id}/ban")
    fun ban(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
        @RequestBody req: BanRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        // 敏感操作二次确认：校验操作者当前密码
        val adminUser = userService.findById(admin.id) ?: throw ForbiddenException("操作者账号不存在")
        if (!passwordEncoder.matches(req.confirmPassword, adminUser.password)) {
            throw ForbiddenException("管理员密码不正确")
        }
        val user = userService.findById(id) ?: throw UserNotFoundException()
        userService.ensureNotLastSuperAdmin(user)
        user.status = Status.BANNED
        userService.save(user)
        // 封禁立即下线：JwtAuthenticationFilter 不读 status，必须靠清白名单让 access token 失效
        tokenStore.invalidateAllSessions(id)
        operationLogService.record(
            operator = admin,
            action = "USER_BAN",
            targetType = "USER",
            targetId = id,
            description = "封禁用户 ${user.username}" +
                (req.reason?.takeIf { it.isNotBlank() }?.let { "，原因 $it" } ?: "") +
                (req.endTime?.let { "，至 $it" } ?: ""),
        )
        return builder.ok().message("用户已封禁").build()
    }

    /** 总管理解封用户：置 ACTIVE，埋审计。 */
    @PostMapping("/{id}/unban")
    fun unban(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val user = userService.findById(id) ?: throw UserNotFoundException()
        user.status = Status.ACTIVE
        userService.save(user)
        operationLogService.record(
            operator = admin,
            action = "USER_UNBAN",
            targetType = "USER",
            targetId = id,
            description = "解封用户 ${user.username}",
        )
        return builder.ok().message("用户已解封").build()
    }

    /** 总管理授予普通用户项目创建资格（设计 §2.2 / §4.2）。 */
    @PostMapping("/{id}/grant-create-project")
    fun grantCreateProject(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
        @RequestBody req: GrantCreateProjectRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val user = userService.findById(id) ?: throw UserNotFoundException()
        user.canCreateProject = true
        userService.save(user)
        operationLogService.record(
            operator = admin,
            action = "GRANT_CREATE_PROJECT",
            targetType = "USER",
            targetId = id,
            description = "授予项目创建资格：${user.username}" + (req.reason?.takeIf { it.isNotBlank() }?.let { "，原因 $it" } ?: ""),
        )
        return builder.ok().message("已授予项目创建资格").build()
    }

    /** 总管理撤销普通用户项目创建资格（设计 §2.2：撤权不影响已拥有的项目）。 */
    @PostMapping("/{id}/revoke-create-project")
    fun revokeCreateProject(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
        @RequestBody req: GrantCreateProjectRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val user = userService.findById(id) ?: throw UserNotFoundException()
        user.canCreateProject = false
        userService.save(user)
        operationLogService.record(
            operator = admin,
            action = "REVOKE_CREATE_PROJECT",
            targetType = "USER",
            targetId = id,
            description = "撤销项目创建资格：${user.username}" + (req.reason?.takeIf { it.isNotBlank() }?.let { "，原因 $it" } ?: ""),
        )
        return builder.ok().message("已撤销项目创建资格").build()
    }
}
