package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** 项目条目管理接口（/api/admin/project/object-items）：凭控制密码管理项目、评论、动态、申请。 */
@RestController
@RequestMapping("/api/admin/project/object-items")
class AdminObjectController(
    private val objectItemManagementService: ObjectItemManagementService,
    private val joinApplicationManagementService: JoinApplicationManagementService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
    private val objectItemCommentManagementService: ObjectItemCommentManagementService,
    private val builder: ResponseBuilder,
) {
    @PostMapping("/{id}/verify")
    fun verify(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemManageVerifyRequest,
    ): ResponseEntity<Response> {
        val item = objectItemManagementService.verify(id, request)
        return builder.ok().data(item).build()
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemManageUpdateRequest,
    ): ResponseEntity<Response> {
        val item = objectItemManagementService.update(id, request)
        return builder.ok().data(item).build()
    }

    @PatchMapping("/{id}/password")
    fun changePassword(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemPasswordChangeRequest,
    ): ResponseEntity<Response> {
        val item = objectItemManagementService.changePassword(id, request)
        return builder.ok().data(item).build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemManageVerifyRequest,
    ): ResponseEntity<Response> {
        val item = objectItemManagementService.delete(id, request)
        return builder.ok().data(item).build()
    }

    @GetMapping("/{id}/join-applications")
    fun listJoinApplications(
        @PathVariable id: Int,
        @RequestParam(required = false) status: JoinApplicationStatus?,
        @RequestParam controlPassword: String,
    ): ResponseEntity<Response> {
        val applications = joinApplicationManagementService.list(
            id,
            status,
            ObjectItemManageVerifyRequest(controlPassword),
        )
        return builder.ok().data(applications).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/accept")
    fun acceptJoinApplication(
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
        @RequestBody request: ObjectItemManageVerifyRequest,
    ): ResponseEntity<Response> {
        val application = joinApplicationManagementService.accept(id, applicationId, request)
        return builder.ok().data(application).build()
    }

    @PostMapping("/{id}/join-applications/{applicationId}/reject")
    fun rejectJoinApplication(
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
        @RequestBody request: JoinApplicationRejectRequest,
    ): ResponseEntity<Response> {
        val application = joinApplicationManagementService.reject(id, applicationId, request)
        return builder.ok().data(application).build()
    }

    @GetMapping("/{id}/updates")
    fun listUpdates(
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemUpdateStatus?,
        @RequestParam controlPassword: String,
    ): ResponseEntity<Response> {
        val updates = objectItemUpdateManagementService.list(
            id,
            status,
            ObjectItemManageVerifyRequest(controlPassword),
        )
        return builder.ok().data(updates).build()
    }

    @PostMapping("/{id}/updates")
    fun createUpdate(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemUpdateManageCreateRequest,
    ): ResponseEntity<Response> {
        val update = objectItemUpdateManagementService.create(id, request)
        return builder.ok().data(update).build()
    }

    @PutMapping("/{id}/updates/{updateId}")
    fun updateUpdate(
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestBody request: ObjectItemUpdateManageUpdateRequest,
    ): ResponseEntity<Response> {
        val update = objectItemUpdateManagementService.update(id, updateId, request)
        return builder.ok().data(update).build()
    }

    @DeleteMapping("/{id}/updates/{updateId}")
    fun deleteUpdate(
        @PathVariable id: Int,
        @PathVariable updateId: Int,
        @RequestParam controlPassword: String,
    ): ResponseEntity<Response> {
        objectItemUpdateManagementService.delete(
            id,
            updateId,
            ObjectItemManageVerifyRequest(controlPassword),
        )
        return builder.ok().build()
    }

    @GetMapping("/{id}/comments")
    fun listComments(
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemCommentStatus?,
        @RequestParam controlPassword: String,
    ): ResponseEntity<Response> {
        val comments = objectItemCommentManagementService.list(
            id,
            status,
            ObjectItemManageVerifyRequest(controlPassword),
        )
        return builder.ok().data(comments).build()
    }

    @PatchMapping("/{id}/comments/{commentId}/status")
    fun reviewComment(
        @PathVariable id: Int,
        @PathVariable commentId: Int,
        @RequestBody request: ObjectItemCommentManageStatusRequest,
    ): ResponseEntity<Response> {
        val comment = objectItemCommentManagementService.review(id, commentId, request)
        return builder.ok().data(comment).build()
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    fun deleteComment(
        @PathVariable id: Int,
        @PathVariable commentId: Int,
        @RequestParam controlPassword: String,
    ): ResponseEntity<Response> {
        objectItemCommentManagementService.delete(
            id,
            commentId,
            ObjectItemManageVerifyRequest(controlPassword),
        )
        return builder.ok().build()
    }
}
