package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/** 想法公开接口（/api/project/minds）。 */
@RestController
@RequestMapping("/api/project/minds")
class MindController(
    private val mindService: MindService,
    private val accessService: AccessService,
    private val builder: ResponseBuilder,
) {

    @GetMapping("/count/approved")
    fun countApproved(): ResponseEntity<Response> {
        val count = mindService.countApproved()
        return builder.ok().data(count).build()
    }

    @PostMapping
    fun save(@RequestBody request: MindSaveRequest): ResponseEntity<Response> {
        val mind = mindService.save(request)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = Response(
            id = mind.id,
            title = mind.title,
            nickName = mind.nickName,
            status = mind.status,
            content = mind.content,
            mcId = mind.mcId,
            createTime = mind.createTime,
            updateTime = mind.updateTime,
        )

        return builder.ok().data(rs).build()
    }

    @PostMapping("/batch")
    fun saveBatch(@RequestBody request: MindBatchSaveRequest): ResponseEntity<Response> {
        val minds = mindService.saveBatch(request.items)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = minds.map {
            Response(
                id = it.id,
                title = it.title,
                nickName = it.nickName,
                status = it.status,
                content = it.content,
                mcId = it.mcId,
                createTime = it.createTime,
                updateTime = it.updateTime,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): ResponseEntity<Response> {
        val mind = mindService.findById(id)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = Response(
            id = mind.id,
            title = mind.title,
            nickName = mind.nickName,
            status = mind.status,
            content = mind.content,
            mcId = mind.mcId,
            createTime = mind.createTime,
            updateTime = mind.updateTime,
        )

        return builder.ok().data(rs).build()
    }

    @GetMapping("/status/{status}")
    fun listByStatus(@PathVariable status: MindStatus): ResponseEntity<Response> {
        val minds = mindService.findByStatus(status)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = minds.map {
            Response(
                id = it.id,
                title = it.title,
                nickName = it.nickName,
                status = it.status,
                content = it.content,
                mcId = it.mcId,
                createTime = it.createTime,
                updateTime = it.updateTime,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping("/statuses")
    fun listByStatuses(@RequestParam statuses: List<MindStatus>): ResponseEntity<Response> {
        val minds = mindService.findByStatuses(statuses)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = minds.map {
            Response(
                id = it.id,
                title = it.title,
                nickName = it.nickName,
                status = it.status,
                content = it.content,
                mcId = it.mcId,
                createTime = it.createTime,
                updateTime = it.updateTime,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) ids: List<Int>?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) nickName: String?,
        @RequestParam(required = false) status: MindStatus?,
        @RequestParam(required = false) statuses: List<MindStatus>?,
        @RequestParam(required = false) mcId: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sort: String?,
    ): ResponseEntity<Response> {
        val request = MindQueryRequest(
            ids = ids,
            title = title,
            nickName = nickName,
            status = status,
            statuses = statuses,
            mcId = mcId,
        )

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        data class PageResponse(
            val content: List<Response>,
            val totalElements: Long,
            val totalPages: Int,
            val page: Int,
            val size: Int,
        )

        val rs: Any = if (page != null || size != null) {
            val vo = mindService.queryPage(request, page ?: 0, size ?: DEFAULT_PAGE_SIZE, sort ?: DEFAULT_SORT)
            PageResponse(
                content = vo.content.map {
                    Response(
                        id = it.id,
                        title = it.title,
                        nickName = it.nickName,
                        status = it.status,
                        content = it.content,
                        mcId = it.mcId,
                        createTime = it.createTime,
                        updateTime = it.updateTime,
                    )
                },
                totalElements = vo.totalElements,
                totalPages = vo.totalPages,
                page = vo.page,
                size = vo.size,
            )
        } else {
            mindService.query(request).map {
                Response(
                    id = it.id,
                    title = it.title,
                    nickName = it.nickName,
                    status = it.status,
                    content = it.content,
                    mcId = it.mcId,
                    createTime = it.createTime,
                    updateTime = it.updateTime,
                )
            }
        }

        return builder.ok().data(rs).build()
    }

    @PostMapping("/query")
    fun query(@RequestBody request: MindQueryRequest): ResponseEntity<Response> {
        val minds = mindService.query(request)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = minds.map {
            Response(
                id = it.id,
                title = it.title,
                nickName = it.nickName,
                status = it.status,
                content = it.content,
                mcId = it.mcId,
                createTime = it.createTime,
                updateTime = it.updateTime,
            )
        }

        return builder.ok().data(rs).build()
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
        @RequestBody request: MindUpdateRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val mind = mindService.update(id, request)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = Response(
            id = mind.id,
            title = mind.title,
            nickName = mind.nickName,
            status = mind.status,
            content = mind.content,
            mcId = mind.mcId,
            createTime = mind.createTime,
            updateTime = mind.updateTime,
        )

        return builder.ok().data(rs).build()
    }

    @PutMapping("/batch")
    fun updateBatch(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: MindBatchUpdateRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val minds = mindService.updateBatch(request.items)

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = minds.map {
            Response(
                id = it.id,
                title = it.title,
                nickName = it.nickName,
                status = it.status,
                content = it.content,
                mcId = it.mcId,
                createTime = it.createTime,
                updateTime = it.updateTime,
            )
        }

        return builder.ok().data(rs).build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Int,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        mindService.delete(id)

        data class Response(
            val deleted: Boolean,
            val id: Int,
        )

        val rs = Response(
            deleted = true,
            id = id,
        )

        return builder.ok().data(rs).build()
    }

    @DeleteMapping("/batch")
    fun deleteBatch(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: MindBatchDeleteRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        mindService.deleteBatch(request.ids)

        data class Response(
            val deleted: Boolean,
            val ids: List<Int>,
        )

        val rs = Response(
            deleted = true,
            ids = request.ids,
        )

        return builder.ok().data(rs).build()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SORT = "createTime,desc"
    }
}
