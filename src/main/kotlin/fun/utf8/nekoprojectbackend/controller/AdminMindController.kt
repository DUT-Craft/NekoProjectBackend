package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** 管理端想法接口（/api/admin/minds）：分页列表与批量改状态。 */
@RestController
@RequestMapping("/api/admin/minds")
class AdminMindController(
    private val mindService: MindService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) statuses: List<MindStatus>?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) nickName: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sort: String?,
    ): ResponseEntity<Response> {
        val request = MindQueryRequest(
            title = title,
            nickName = nickName,
            statuses = statuses,
        )
        val vo = mindService.queryPage(
            request,
            page ?: DEFAULT_PAGE,
            size ?: DEFAULT_PAGE_SIZE,
            sort ?: DEFAULT_SORT,
        )

        data class AdminMindPage(
            val list: List<MindResponse>,
            val total: Long,
            val page: Int,
            val size: Int,
        )

        val rs = AdminMindPage(
            list = vo.content,
            total = vo.totalElements,
            page = vo.page,
            size = vo.size,
        )
        return builder.ok().data(rs).build()
    }

    @PutMapping("/batch/status")
    fun batchStatus(@RequestBody request: AdminBatchStatusRequest<MindStatus>): ResponseEntity<Response> {
        val updateRequests = request.ids.map {
            MindUpdateRequest(id = it, status = request.status)
        }
        mindService.updateBatch(updateRequests)

        data class Response(
            val updated: Boolean,
            val ids: List<Int>,
            val status: MindStatus,
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
        const val DEFAULT_SORT = "createTime,desc"
    }
}
