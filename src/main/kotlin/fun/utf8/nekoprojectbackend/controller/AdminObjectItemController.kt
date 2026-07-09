package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** 管理端项目条目接口（/api/admin/object-items）：分页列表与批量改状态。 */
@RestController
@RequestMapping("/api/admin/object-items")
class AdminObjectItemController(
    private val objectItemService: ObjectItemService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun list(
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
    fun batchStatus(@RequestBody request: AdminBatchStatusRequest<ObjectItemStatus>): ResponseEntity<Response> {
        val updateRequests = request.ids.map {
            ObjectItemUpdateRequest(id = it, status = request.status)
        }
        objectItemService.updateBatch(updateRequests)

        data class Response(
            val updated: Boolean,
            val ids: List<Int>,
            val status: ObjectItemStatus,
        )

        val rs = Response(
            updated = true,
            ids = request.ids,
            status = request.status,
        )
        return builder.ok().data(rs).build()
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SORT = "id,desc"
    }
}
