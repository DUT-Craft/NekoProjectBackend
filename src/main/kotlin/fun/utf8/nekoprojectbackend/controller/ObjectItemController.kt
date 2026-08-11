package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
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

/** 项目条目公开接口（/api/project/object-items）：增删改查、评论、动态、加入申请。 */
@RestController
@RequestMapping("/api/project/object-items")
class ObjectItemController(
    private val objectItemService: ObjectItemService,
    private val objectItemUpdateService: ObjectItemUpdateService,
    private val objectItemCommentService: ObjectItemCommentService,
    private val joinApplicationService: JoinApplicationService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val rateLimiter: RateLimiter,
    private val clientRequestIdentity: ClientRequestIdentity,
    private val builder: ResponseBuilder,
) {

    @GetMapping("/count/in-progress")
    fun countInProgress(): ResponseEntity<Response> {
        val count = objectItemService.countInProgress()
        return builder.ok().data(count).build()
    }

    @GetMapping("/count/public")
    fun countPublic(): ResponseEntity<Response> {
        val count = objectItemService.countPublic()
        return builder.ok().data(count).build()
    }

    @PostMapping
    fun save(
        @Valid @RequestBody request: ObjectItemSaveRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        limitAnonymousWrite(servletRequest, "project", MAX_PROJECT_SUBMISSIONS_PER_HOUR)
        val item = objectItemService.save(
            request.copy(
                controlPassword = ProjectControlPasswordPolicy.normalizeRequired(request.controlPassword),
            ),
        )
        operationLogService.record(
            action = "PROJECT_CREATE",
            targetType = "PROJECT",
            targetId = item.id,
            description = "提交项目《${request.title}》",
        )

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
        )

        val rs = Response(
            id = item.id,
            title = item.title,
            type = item.type,
            introduction = item.introduction,
            description = item.description,
            status = item.status,
            leader = item.leader,
            needMembers = item.needMembers,
            tags = item.tags,
            leaderMcId = item.leaderMcId,
            contactInformation = item.contactInformation,
            coverImageUrl = item.coverImageUrl,
            progress = item.progress,
        )

        return builder.ok().data(rs).build()
    }

    @PostMapping("/batch")
    fun saveBatch(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: ObjectItemBatchSaveRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val items = objectItemService.saveBatch(request.items)

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
            val hasControlPassword: Boolean,
        )

        val rs = items.map {
            Response(
                id = it.id,
                title = it.title,
                type = it.type,
                introduction = it.introduction,
                description = it.description,
                status = it.status,
                leader = it.leader,
                needMembers = it.needMembers,
                tags = it.tags,
                leaderMcId = it.leaderMcId,
                contactInformation = it.contactInformation,
                coverImageUrl = it.coverImageUrl,
                progress = it.progress,
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): ResponseEntity<Response> {
        val item = objectItemService.findPublicById(id)

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
        )

        val rs = Response(
            id = item.id,
            title = item.title,
            type = item.type,
            introduction = item.introduction,
            description = item.description,
            status = item.status,
            leader = item.leader,
            needMembers = item.needMembers,
            tags = item.tags,
            leaderMcId = item.leaderMcId,
            contactInformation = item.contactInformation,
            coverImageUrl = item.coverImageUrl,
            progress = item.progress,
        )

        return builder.ok().data(rs).build()
    }

    @GetMapping("/status/{status}")
    fun listByStatus(@PathVariable status: ObjectItemStatus): ResponseEntity<Response> {
        val items = objectItemService.findPublicByStatus(status)

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
        )

        val rs = items.map {
            Response(
                id = it.id,
                title = it.title,
                type = it.type,
                introduction = it.introduction,
                description = it.description,
                status = it.status,
                leader = it.leader,
                needMembers = it.needMembers,
                tags = it.tags,
                leaderMcId = it.leaderMcId,
                contactInformation = it.contactInformation,
                coverImageUrl = it.coverImageUrl,
                progress = it.progress,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) ids: List<Int>?,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: ObjectItemStatus?,
        @RequestParam(required = false) statuses: List<ObjectItemStatus>?,
        @RequestParam(required = false) leader: String?,
        @RequestParam(required = false) leaderMcId: String?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sort: String?,
    ): ResponseEntity<Response> {
        val request = ObjectItemQueryRequest(
            ids = ids,
            title = title,
            type = type,
            status = status,
            statuses = statuses,
            leader = leader,
            leaderMcId = leaderMcId,
            tags = tags,
        )

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
        )

        data class PageResponse(
            val content: List<Response>,
            val totalElements: Long,
            val totalPages: Int,
            val page: Int,
            val size: Int,
        )

        val rs: Any = if (page != null || size != null) {
            val vo = objectItemService.queryPublicPage(
                request,
                page ?: 0,
                size ?: DEFAULT_PAGE_SIZE,
                sort ?: DEFAULT_SORT,
            )
            PageResponse(
                content = vo.content.map {
                    Response(
                        id = it.id,
                        title = it.title,
                        type = it.type,
                        introduction = it.introduction,
                        description = it.description,
                        status = it.status,
                        leader = it.leader,
                        needMembers = it.needMembers,
                        tags = it.tags,
                        leaderMcId = it.leaderMcId,
                        contactInformation = it.contactInformation,
                        coverImageUrl = it.coverImageUrl,
                        progress = it.progress,
                    )
                },
                totalElements = vo.totalElements,
                totalPages = vo.totalPages,
                page = vo.page,
                size = vo.size,
            )
        } else {
            objectItemService.queryPublic(request).map {
                Response(
                    id = it.id,
                    title = it.title,
                    type = it.type,
                    introduction = it.introduction,
                    description = it.description,
                    status = it.status,
                    leader = it.leader,
                    needMembers = it.needMembers,
                    tags = it.tags,
                    leaderMcId = it.leaderMcId,
                    contactInformation = it.contactInformation,
                    coverImageUrl = it.coverImageUrl,
                    progress = it.progress,
                )
            }
        }

        return builder.ok().data(rs).build()
    }

    @PostMapping("/query")
    fun query(
        @AuthenticationPrincipal admin: LoginUser,
        @Valid @RequestBody request: ObjectItemQueryRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val items = objectItemService.query(request)

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
            val hasControlPassword: Boolean,
        )

        val rs = items.map {
            Response(
                id = it.id,
                title = it.title,
                type = it.type,
                introduction = it.introduction,
                description = it.description,
                status = it.status,
                leader = it.leader,
                needMembers = it.needMembers,
                tags = it.tags,
                leaderMcId = it.leaderMcId,
                contactInformation = it.contactInformation,
                coverImageUrl = it.coverImageUrl,
                progress = it.progress,
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
        @Valid @RequestBody request: ObjectItemUpdateRequest,
    ): ResponseEntity<Response> {
        val current = accessService.ensureCanManage(user, id)
        accessService.ensureCanSetProjectStatus(user, current.status, request.status)
        // 控制密码只属于项目方自服务接口；JWT 管理更新不得借此改写它。
        val item = objectItemService.update(id, request.copy(controlPassword = null))
        operationLogService.record(
            action = "PROJECT_UPDATE",
            targetType = "PROJECT",
            targetId = id,
            description = "更新项目 #$id",
        )

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
            val hasControlPassword: Boolean,
        )

        val rs = Response(
            id = item.id,
            title = item.title,
            type = item.type,
            introduction = item.introduction,
            description = item.description,
            status = item.status,
            leader = item.leader,
            needMembers = item.needMembers,
            tags = item.tags,
            leaderMcId = item.leaderMcId,
            contactInformation = item.contactInformation,
            coverImageUrl = item.coverImageUrl,
            progress = item.progress,
            hasControlPassword = item.hasControlPassword,
        )

        return builder.ok().data(rs).build()
    }

    @PutMapping("/batch")
    fun updateBatch(
        @AuthenticationPrincipal user: LoginUser,
        @Valid @RequestBody request: ObjectItemBatchUpdateRequest,
    ): ResponseEntity<Response> {
        request.items.forEach {
            val id = it.id ?: throw ParamErrorException("批量更新时项目条目 ID 不能为空")
            val current = accessService.ensureCanManage(user, id)
            accessService.ensureCanSetProjectStatus(user, current.status, it.status)
        }
        // 控制密码只属于项目方自服务接口；JWT 管理更新不得借此改写它。
        val items = objectItemService.updateBatch(request.items.map { it.copy(controlPassword = null) })

        data class Response(
            val id: Int?,
            val title: String?,
            val type: String?,
            val introduction: String?,
            val description: String?,
            val status: ObjectItemStatus?,
            val leader: String?,
            val needMembers: List<NeedMemberItemResponse>,
            val tags: List<String>,
            val leaderMcId: String?,
            val contactInformation: String?,
            val coverImageUrl: String?,
            val progress: Int,
            val hasControlPassword: Boolean,
        )

        val rs = items.map {
            Response(
                id = it.id,
                title = it.title,
                type = it.type,
                introduction = it.introduction,
                description = it.description,
                status = it.status,
                leader = it.leader,
                needMembers = it.needMembers,
                tags = it.tags,
                leaderMcId = it.leaderMcId,
                contactInformation = it.contactInformation,
                coverImageUrl = it.coverImageUrl,
                progress = it.progress,
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @DeleteMapping("/batch")
    fun deleteBatch(
        @AuthenticationPrincipal user: LoginUser,
        @Valid @RequestBody request: ObjectItemBatchDeleteRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(user)
        request.ids.forEach { accessService.ensureCanManage(user, it) }
        objectItemService.deleteBatch(request.ids)
        operationLogService.record(
            action = "PROJECT_DELETE",
            targetType = "PROJECT",
            targetId = request.ids,
            description = "批量删除项目 ${request.ids}",
        )

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

    @GetMapping("/{id}/updates")
    fun listUpdates(
        @PathVariable id: Int,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Int?,
            val objectItemId: Int?,
            val title: String?,
            val content: String?,
            val imageUrl: String?,
            val status: ObjectItemUpdateStatus?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs: Any = if (page != null || size != null) {
            objectItemUpdateService.findByObjectItemPage(
                id,
                page ?: DEFAULT_SUBRESOURCE_PAGE,
                size ?: DEFAULT_SUBRESOURCE_PAGE_SIZE,
            )
        } else {
            objectItemUpdateService.findByObjectItem(id).map {
                Response(
                    id = it.id,
                    objectItemId = it.objectItemId,
                    title = it.title,
                    content = it.content,
                    imageUrl = it.imageUrl,
                    status = it.status,
                    createTime = it.createTime,
                    updateTime = it.updateTime,
                )
            }
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping("/{id}/comments")
    fun listComments(
        @PathVariable id: Int,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Int?,
            val objectItemId: Int?,
            val nickName: String?,
            val content: String?,
            val status: ObjectItemCommentStatus?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs: Any = if (page != null || size != null) {
            objectItemCommentService.findByObjectItemPage(
                id,
                page ?: DEFAULT_SUBRESOURCE_PAGE,
                size ?: DEFAULT_SUBRESOURCE_PAGE_SIZE,
            )
        } else {
            objectItemCommentService.findByObjectItem(id).map {
                Response(
                    id = it.id,
                    objectItemId = it.objectItemId,
                    nickName = it.nickName,
                    content = it.content,
                    status = it.status,
                    createTime = it.createTime,
                    updateTime = it.updateTime,
                )
            }
        }

        return builder.ok().data(rs).build()
    }

    @PostMapping("/{id}/comments")
    fun createComment(
        @PathVariable id: Int,
        @Valid @RequestBody request: ObjectItemCommentSaveRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        limitAnonymousWrite(servletRequest, "comment", MAX_COMMENT_SUBMISSIONS_PER_HOUR)
        val comment = objectItemCommentService.create(id, request)

        data class Response(
            val id: Int?,
            val objectItemId: Int?,
            val nickName: String?,
            val content: String?,
            val status: ObjectItemCommentStatus?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = Response(
            id = comment.id,
            objectItemId = comment.objectItemId,
            nickName = comment.nickName,
            content = comment.content,
            status = comment.status,
            createTime = comment.createTime,
            updateTime = comment.updateTime,
        )

        return builder.ok().data(rs).build()
    }

    @PostMapping("/{id}/join-applications")
    fun createJoinApplication(
        @PathVariable id: Int,
        @Valid @RequestBody request: JoinApplicationSaveRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        limitAnonymousWrite(servletRequest, "join", MAX_JOIN_SUBMISSIONS_PER_HOUR)
        val saved = joinApplicationService.createTracked(id, request)
        val application = saved.value

        data class Response(
            val id: Int?,
            val objectItemId: Int?,
            val nickName: String?,
            val mcId: String?,
            val contact: String?,
            val reason: String?,
            val skill: String?,
            val status: JoinApplicationStatus?,
            val rejectReason: String?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
            val trackingToken: String,
        )

        val rs = Response(
            id = application.id,
            objectItemId = application.objectItemId,
            nickName = application.nickName,
            mcId = application.mcId,
            contact = application.contact,
            reason = application.reason,
            skill = application.skill,
            status = application.status,
            rejectReason = application.rejectReason,
            createTime = application.createTime,
            updateTime = application.updateTime,
            trackingToken = saved.trackingToken,
        )

        return builder.ok().data(rs).build()
    }

    /** 游客凭提交成功时展示的一次性追踪码查询加入申请状态。 */
    @GetMapping("/{id}/join-applications/{applicationId}/status")
    fun getTrackedJoinApplicationStatus(
        @PathVariable id: Int,
        @PathVariable applicationId: Int,
        @RequestHeader(SUBMISSION_TRACKING_TOKEN_HEADER) trackingToken: String,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Response> {
        rateLimiter.consume(
            "tracking-status-ip",
            clientRequestIdentity.clientIp(servletRequest),
            MAX_TRACKING_READS_PER_HOUR,
            RATE_LIMIT_WINDOW,
        )
        val application = joinApplicationService.findTracked(id, applicationId, trackingToken)
        return builder.ok().data(application).build()
    }

    private fun limitAnonymousWrite(request: HttpServletRequest, type: String, limit: Int) {
        val clientIp = clientRequestIdentity.clientIp(request)
        rateLimiter.consume("public-write-ip", clientIp, MAX_PUBLIC_WRITES_PER_HOUR, RATE_LIMIT_WINDOW)
        rateLimiter.consume("public-$type-ip", clientIp, limit, RATE_LIMIT_WINDOW)
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SUBRESOURCE_PAGE = 0
        const val DEFAULT_SUBRESOURCE_PAGE_SIZE = 100
        const val DEFAULT_SORT = "id,desc"
        const val MAX_PUBLIC_WRITES_PER_HOUR = 80
        const val MAX_PROJECT_SUBMISSIONS_PER_HOUR = 10
        const val MAX_COMMENT_SUBMISSIONS_PER_HOUR = 40
        const val MAX_JOIN_SUBMISSIONS_PER_HOUR = 15
        const val MAX_TRACKING_READS_PER_HOUR = 120
        val RATE_LIMIT_WINDOW: Duration = Duration.ofHours(1)
    }
}
