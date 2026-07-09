package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentManagementService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateManagementService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemModerationController(
    private val objectItemCommentManagementService: ObjectItemCommentManagementService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
    private val builder: ResponseBuilder,
) {
    @PatchMapping("/{id}/comments/{commentId}/status")
    fun reviewComment(
        @PathVariable id: Int,
        @PathVariable commentId: Int,
        @RequestBody request: CommentStatusRequest,
    ): ResponseEntity<Response> {
        val comment = objectItemCommentManagementService.reviewByAdmin(id, commentId, request.status)
        return builder.ok().data(comment).build()
    }

    @PatchMapping("/{id}/updates/{updateId}/status")
    fun reviewUpdate(
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestBody request: UpdateStatusRequest,
    ): ResponseEntity<Response> {
        val update = objectItemUpdateManagementService.reviewByAdmin(id, updateId, request.status)
        return builder.ok().data(update).build()
    }

    data class CommentStatusRequest(
        val status: ObjectItemCommentStatus = ObjectItemCommentStatus.APPROVED,
    )

    data class UpdateStatusRequest(
        val status: ObjectItemUpdateStatus = ObjectItemUpdateStatus.APPROVED,
    )
}
