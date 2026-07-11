package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 管理端「单个项目维护」接口（/api/admin/object-items/{id}/...）：JWT 鉴权，无需项目控制密码。
 *
 * 总管理可维护任意项目；项目管理仅能维护名下项目（由 [AccessService.ensureCanManage] 校验）。
 * 评论 / 动态的状态审核仍走 [AdminObjectItemModerationController]。
 */
@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemMaintenanceController(
    private val joinApplicationManagementService: JoinApplicationManagementService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
    private val objectItemCommentManagementService: ObjectItemCommentManagementService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {

    /* ---------- 加入申请 ---------- */

    @GetMapping("/{id}/join-applications")
    fun listJoinApplications(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestParam(required = false) status: JoinApplicationStatus?,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val applications = joinApplicationManagementService.listByAdmin(id, status)
        return builder.ok().data(applications).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/accept")
    fun acceptJoinApplication(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val application = joinApplicationManagementService.acceptByAdmin(id, applicationId)
        operationLogService.record(
            operator = admin,
            action = "JOIN_ACCEPT",
            targetType = "JOIN_APPLICATION",
            targetId = applicationId,
            description = "同意加入申请 #$applicationId（项目 #$id）",
        )
        return builder.ok().data(application).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/reject")
    fun rejectJoinApplication(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
        @RequestBody(required = false) request: JoinApplicationAdminRejectRequest?,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val application = joinApplicationManagementService.rejectByAdmin(
            id,
            applicationId,
            request?.rejectReason,
        )
        operationLogService.record(
            operator = admin,
            action = "JOIN_REJECT",
            targetType = "JOIN_APPLICATION",
            targetId = applicationId,
            description = "拒绝加入申请 #$applicationId（项目 #$id）",
        )
        return builder.ok().data(application).build()
    }

    /* ---------- 项目动态 ---------- */

    @GetMapping("/{id}/updates")
    fun listUpdates(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemUpdateStatus?,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val updates = objectItemUpdateManagementService.listByAdmin(id, status)
        return builder.ok().data(updates).build()
    }

    @PostMapping("/{id}/updates")
    fun createUpdate(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestBody request: ObjectItemUpdateManageCreateRequest,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val update = objectItemUpdateManagementService.createByAdmin(id, request)
        operationLogService.record(
            operator = admin,
            action = "UPDATE_CREATE",
            targetType = "PROJECT_UPDATE",
            targetId = update.id,
            description = "发布项目动态《${request.title}》（项目 #$id）",
        )
        return builder.ok().data(update).build()
    }

    @PutMapping("/{id}/updates/{updateId}")
    fun updateUpdate(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestBody request: ObjectItemUpdateManageUpdateRequest,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val update = objectItemUpdateManagementService.updateByAdmin(id, updateId, request)
        operationLogService.record(
            operator = admin,
            action = "UPDATE_UPDATE",
            targetType = "PROJECT_UPDATE",
            targetId = updateId,
            description = "更新项目动态 #$updateId（项目 #$id）",
        )
        return builder.ok().data(update).build()
    }

    @DeleteMapping("/{id}/updates/{updateId}")
    fun deleteUpdate(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable updateId: Int,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        objectItemUpdateManagementService.deleteByAdmin(id, updateId)
        operationLogService.record(
            operator = admin,
            action = "UPDATE_DELETE",
            targetType = "PROJECT_UPDATE",
            targetId = updateId,
            description = "删除项目动态 #$updateId（项目 #$id）",
        )
        return builder.ok().build()
    }

    /* ---------- 项目评论 ---------- */

    @GetMapping("/{id}/comments")
    fun listComments(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemCommentStatus?,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val comments = objectItemCommentManagementService.listByAdmin(id, status)
        return builder.ok().data(comments).build()
    }
}
