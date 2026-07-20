package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.AddMemberRequest
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.ProjectMemberService
import `fun`.utf8.nekoprojectbackend.service.TransferOwnerRequest
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 项目成员管理接口（设计 §9）：列成员 / 添加 / 移除 / 转交所有权 / 主动退出。
 *
 * - 列成员（GET）公开可读（落在 SecurityConfig 的 GET 放行规则下，/api/project 前缀）；
 * - 其余写操作需 JWT：添加 / 移除 / 转交限 OWNER（或超管），退出限成员本人。
 */
@RestController
class ProjectMemberController(
    private val projectMemberService: ProjectMemberService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    /** 列出项目活跃成员（按 OWNER → MANAGER → MEMBER 排序）。 */
    @GetMapping("/api/project/object-items/{id}/members")
    fun list(@PathVariable id: Int): ResponseEntity<Response> =
        builder.ok().data(projectMemberService.listActive(id)).build()

    /** OWNER 添加项目管理员 / 成员。 */
    @PostMapping("/api/project/object-items/{id}/members")
    fun add(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
        @RequestBody req: AddMemberRequest,
    ): ResponseEntity<Response> {
        accessService.ensureIsOwner(user, id)
        val member = projectMemberService.addMember(id, req, invitedBy = user.id)
        operationLogService.record(
            operator = user,
            action = "MEMBER_ADD",
            targetType = "PROJECT",
            targetId = id,
            description = "添加项目 #$id 成员 ${req.userId}（${req.role}）",
        )
        return builder.ok().data(member).build()
    }

    /** OWNER 移除成员或撤销管理员。 */
    @DeleteMapping("/api/project/object-items/{id}/members/{userId}")
    fun remove(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
        @PathVariable userId: Long,
    ): ResponseEntity<Response> {
        accessService.ensureIsOwner(user, id)
        val member = projectMemberService.removeMember(id, userId)
        operationLogService.record(
            operator = user,
            action = "MEMBER_REMOVE",
            targetType = "PROJECT",
            targetId = id,
            description = "移除项目 #$id 成员 $userId",
        )
        return builder.ok().data(member).build()
    }

    /** OWNER 转交所有权（旧 OWNER 降为 MEMBER）。 */
    @PostMapping("/api/project/object-items/{id}/transfer")
    fun transfer(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
        @RequestBody req: TransferOwnerRequest,
    ): ResponseEntity<Response> {
        accessService.ensureIsOwner(user, id)
        val member = projectMemberService.transferOwnership(id, req.newOwnerId)
        operationLogService.record(
            operator = user,
            action = "PROJECT_TRANSFER",
            targetType = "PROJECT",
            targetId = id,
            description = "项目 #$id 所有权转交给 ${req.newOwnerId}",
        )
        return builder.ok().data(member).build()
    }

    /** 成员本人主动退出（OWNER 禁止，提示先转交）。 */
    @PostMapping("/api/project/object-items/{id}/leave")
    fun leave(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
    ): ResponseEntity<Response> {
        val member = projectMemberService.leave(id, user.id)
        operationLogService.record(
            operator = user,
            action = "MEMBER_LEAVE",
            targetType = "PROJECT",
            targetId = id,
            description = "用户退出项目 #$id",
        )
        return builder.ok().data(member).build()
    }
}
