package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentManagementService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateManagementService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/** 管理端审核接口（/api/admin/object-items）：JWT 鉴权审核评论/动态状态。总管理全局；项目管理限名下项目。 */
@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemModerationController(
    private val objectItemCommentManagementService: ObjectItemCommentManagementService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {
    @PatchMapping("/{id}/comments/{commentId}/status")
    fun reviewComment(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable commentId: Int,
        @RequestBody request: CommentStatusRequest,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val comment = objectItemCommentManagementService.reviewByAdmin(id, commentId, request.status)
        operationLogService.record(
            operator = admin,
            action = "COMMENT_MODERATE",
            targetType = "COMMENT",
            targetId = commentId,
            description = "审核评论 #$commentId → ${request.status}（项目 #$id）",
        )
        return builder.ok().data(comment).build()
    }

    @PatchMapping("/{id}/updates/{updateId}/status")
    fun reviewUpdate(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestBody request: UpdateStatusRequest,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(admin, id)
        val update = objectItemUpdateManagementService.reviewByAdmin(id, updateId, request.status)
        operationLogService.record(
            operator = admin,
            action = "UPDATE_MODERATE",
            targetType = "PROJECT_UPDATE",
            targetId = updateId,
            description = "审核项目动态 #$updateId → ${request.status}（项目 #$id）",
        )
        return builder.ok().data(update).build()
    }

    data class CommentStatusRequest(
        val status: ObjectItemCommentStatus = ObjectItemCommentStatus.APPROVED,
    )

    data class UpdateStatusRequest(
        val status: ObjectItemUpdateStatus = ObjectItemUpdateStatus.APPROVED,
    )
}
