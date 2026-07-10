package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 管理端「单个项目维护」接口（/api/admin/object-items/{id}/...）：JWT 鉴权，无需项目控制密码。
 *
 * 对标项目方自服务（/api/admin/project/object-items/{id}/...），让管理员以全局身份维护单个项目的
 * 加入申请、动态、评论，与项目方后台能力对齐。评论 / 动态的状态审核仍走 [AdminObjectItemModerationController]。
 */
@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemMaintenanceController(
    private val joinApplicationManagementService: JoinApplicationManagementService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
    private val objectItemCommentManagementService: ObjectItemCommentManagementService,
    private val builder: ResponseBuilder,
) {

    /* ---------- 加入申请 ---------- */

    @GetMapping("/{id}/join-applications")
    fun listJoinApplications(
        @PathVariable id: Int,
        @RequestParam(required = false) status: JoinApplicationStatus?,
    ): ResponseEntity<Response> {
        val applications = joinApplicationManagementService.listByAdmin(id, status)
        return builder.ok().data(applications).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/accept")
    fun acceptJoinApplication(
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
    ): ResponseEntity<Response> {
        val application = joinApplicationManagementService.acceptByAdmin(id, applicationId)
        return builder.ok().data(application).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/reject")
    fun rejectJoinApplication(
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
        @RequestBody(required = false) request: JoinApplicationAdminRejectRequest?,
    ): ResponseEntity<Response> {
        val application = joinApplicationManagementService.rejectByAdmin(
            id,
            applicationId,
            request?.rejectReason,
        )
        return builder.ok().data(application).build()
    }

    /* ---------- 项目动态 ---------- */

    @GetMapping("/{id}/updates")
    fun listUpdates(
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemUpdateStatus?,
    ): ResponseEntity<Response> {
        val updates = objectItemUpdateManagementService.listByAdmin(id, status)
        return builder.ok().data(updates).build()
    }

    @PostMapping("/{id}/updates")
    fun createUpdate(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemUpdateManageCreateRequest,
    ): ResponseEntity<Response> {
        val update = objectItemUpdateManagementService.createByAdmin(id, request)
        return builder.ok().data(update).build()
    }

    @PutMapping("/{id}/updates/{updateId}")
    fun updateUpdate(
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestBody request: ObjectItemUpdateManageUpdateRequest,
    ): ResponseEntity<Response> {
        val update = objectItemUpdateManagementService.updateByAdmin(id, updateId, request)
        return builder.ok().data(update).build()
    }

    @DeleteMapping("/{id}/updates/{updateId}")
    fun deleteUpdate(
        @PathVariable id: Int,
        @PathVariable updateId: Int,
    ): ResponseEntity<Response> {
        objectItemUpdateManagementService.deleteByAdmin(id, updateId)
        return builder.ok().build()
    }

    /* ---------- 项目评论 ---------- */

    @GetMapping("/{id}/comments")
    fun listComments(
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemCommentStatus?,
    ): ResponseEntity<Response> {
        val comments = objectItemCommentManagementService.listByAdmin(id, status)
        return builder.ok().data(comments).build()
    }
}
