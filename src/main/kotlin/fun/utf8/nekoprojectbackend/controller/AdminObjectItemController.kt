package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 管理端项目条目接口（/api/admin/object-items）：JWT 鉴权。
 * 总管理可见/操作全部项目；项目管理仅限名下项目（列表按 ownerId 过滤、操作按归属校验）。
 */
@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemController(
    private val objectItemService: ObjectItemService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestParam(required = false) statuses: List<ObjectItemStatus>?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) leader: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sort: String?,
    ): ResponseEntity<Response> {
        val request = ObjectItemQueryRequest(
            title = title,
            leader = leader,
            statuses = statuses,
            ownerId = accessService.ownerIdScope(admin),
        )
        val vo = objectItemService.queryPage(
            request,
            page ?: DEFAULT_PAGE,
            size ?: DEFAULT_PAGE_SIZE,
            sort ?: DEFAULT_SORT,
        )

        data class AdminObjectItemPage(
            val list: List<ObjectItemResponse>,
            val total: Long,
            val page: Int,
            val size: Int,
        )

        val rs = AdminObjectItemPage(
            list = vo.content,
            total = vo.totalElements,
            page = vo.page,
            size = vo.size,
        )
        return builder.ok().data(rs).build()
    }

    @PutMapping("/batch/status")
    fun batchStatus(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: AdminBatchStatusRequest<ObjectItemStatus>,
    ): ResponseEntity<Response> {
        request.ids.forEach { accessService.ensureCanManage(admin, it) }
        val updateRequests = request.ids.map {
            ObjectItemUpdateRequest(id = it, status = request.status)
        }
        objectItemService.updateBatch(updateRequests)
        operationLogService.record(
            operator = admin,
            action = "PROJECT_STATUS_BATCH",
            targetType = "PROJECT",
            targetId = request.ids,
            description = "批量改项目状态为 ${request.status}：$request.ids",
        )

        data class BatchResult(
            val updated: Boolean,
            val ids: List<Int>,
            val status: ObjectItemStatus,
        )

        val rs = BatchResult(
            updated = true,
            ids = request.ids,
            status = request.status,
        )
        return builder.ok().data(rs).build()
    }

    data class AssignOwnerRequest(val ownerId: Long?)

    /** 总管理把项目分配给项目管理（ownerId=null 表示收回为未分配）。 */
    @PutMapping("/{id}/owner")
    fun assignOwner(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestBody req: AssignOwnerRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val item = objectItemService.assignOwner(id, req.ownerId)
        operationLogService.record(
            operator = admin,
            action = "PROJECT_ASSIGN",
            targetType = "PROJECT",
            targetId = id,
            description = if (req.ownerId == null) {
                "收回项目 #$id（未分配）"
            } else {
                "将项目 #$id 分配给用户 ${req.ownerId}"
            },
        )
        return builder.ok().data(item).build()
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SORT = "id,desc"
    }
}
