package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import `fun`.utf8.nekoprojectbackend.security.ClientRequestIdentity
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.Duration

/** 想法公开接口（/api/project/minds）。 */
@RestController
@RequestMapping("/api/project/minds")
class MindController(
    private val mindService: MindService,
    private val accessService: AccessService,
    private val rateLimiter: RateLimiter,
    private val clientRequestIdentity: ClientRequestIdentity,
    private val builder: ResponseBuilder,
) {

    @GetMapping("/count/approved")
    fun countApproved(): ResponseEntity<Response> {
        val count = mindService.countApproved()
        return builder.ok().data(count).build()
    }

    @PostMapping
    fun save(
        @Valid @RequestBody request: MindSaveRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        val clientIp = clientRequestIdentity.clientIp(servletRequest)
        rateLimiter.consume("public-write-ip", clientIp, MAX_PUBLIC_WRITES_PER_HOUR, RATE_LIMIT_WINDOW)
        rateLimiter.consume("public-idea-ip", clientIp, MAX_IDEA_SUBMISSIONS_PER_HOUR, RATE_LIMIT_WINDOW)
        val saved = mindService.saveTracked(request)
        val mind = saved.value

        data class Response(
            val id: Int?,
            val title: String?,
            val nickName: String?,
            val status: MindStatus?,
            val content: String?,
            val mcId: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
            val trackingToken: String,
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
            trackingToken = saved.trackingToken,
        )

        return builder.ok().data(rs).build()
    }

    /** 游客凭提交成功时展示的一次性追踪码查询想法状态。 */
    @GetMapping("/{id}/status")
    fun getTrackedStatus(
        @PathVariable id: Int,
        @RequestHeader(SUBMISSION_TRACKING_TOKEN_HEADER) trackingToken: String,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        rateLimiter.consume(
            "tracking-status-ip",
            clientRequestIdentity.clientIp(servletRequest),
            MAX_TRACKING_READS_PER_HOUR,
            RATE_LIMIT_WINDOW,
        )
        return builder.ok().data(mindService.findTracked(id, trackingToken)).build()
    }

    @PostMapping("/batch")
    fun saveBatch(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: MindBatchSaveRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
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
        val mind = mindService.findPublicById(id)

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
        val minds = mindService.findPublicByStatus(status)

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
        val minds = mindService.findPublicByStatuses(statuses)

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
            val vo = mindService.queryPublicPage(request, page ?: 0, size ?: DEFAULT_PAGE_SIZE, sort ?: DEFAULT_SORT)
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
            mindService.queryPublic(request).map {
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
    fun query(
        @AuthenticationPrincipal admin: LoginUser,
        @Valid @RequestBody request: MindQueryRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
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
        @Valid @RequestBody request: MindUpdateRequest,
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
        @Valid @RequestBody request: MindBatchUpdateRequest,
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
        @Valid @RequestBody request: MindBatchDeleteRequest,
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
        const val MAX_PUBLIC_WRITES_PER_HOUR = 80
        const val MAX_IDEA_SUBMISSIONS_PER_HOUR = 20
        const val MAX_TRACKING_READS_PER_HOUR = 120
        val RATE_LIMIT_WINDOW: Duration = Duration.ofHours(1)
    }
}
