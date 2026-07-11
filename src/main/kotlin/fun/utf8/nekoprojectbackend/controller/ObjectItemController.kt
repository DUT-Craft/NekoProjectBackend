package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemUpdateStatus
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

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
    private val builder: ResponseBuilder,
) {

    @GetMapping("/count/in-progress")
    fun countInProgress(): ResponseEntity<Response> {
        val count = objectItemService.countInProgress()
        return builder.ok().data(count).build()
    }

    @PostMapping
    fun save(@RequestBody request: ObjectItemSaveRequest): ResponseEntity<Response> {
        val item = objectItemService.save(request)
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
            hasControlPassword = item.hasControlPassword,
        )

        return builder.ok().data(rs).build()
    }

    @PostMapping("/batch")
    fun saveBatch(@RequestBody request: ObjectItemBatchSaveRequest): ResponseEntity<Response> {
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
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): ResponseEntity<Response> {
        val item = objectItemService.findById(id)

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
            hasControlPassword = item.hasControlPassword,
        )

        return builder.ok().data(rs).build()
    }

    @GetMapping("/status/{status}")
    fun listByStatus(@PathVariable status: ObjectItemStatus): ResponseEntity<Response> {
        val items = objectItemService.findByStatus(status)

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
                hasControlPassword = it.hasControlPassword,
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
            val hasControlPassword: Boolean,
        )

        data class PageResponse(
            val content: List<Response>,
            val totalElements: Long,
            val totalPages: Int,
            val page: Int,
            val size: Int,
        )

        val rs: Any = if (page != null || size != null) {
            val vo = objectItemService.queryPage(request, page ?: 0, size ?: DEFAULT_PAGE_SIZE, sort ?: DEFAULT_SORT)
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
                        hasControlPassword = it.hasControlPassword,
                    )
                },
                totalElements = vo.totalElements,
                totalPages = vo.totalPages,
                page = vo.page,
                size = vo.size,
            )
        } else {
            objectItemService.query(request).map {
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
                    hasControlPassword = it.hasControlPassword,
                )
            }
        }

        return builder.ok().data(rs).build()
    }

    @PostMapping("/query")
    fun query(@RequestBody request: ObjectItemQueryRequest): ResponseEntity<Response> {
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
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: LoginUser,
        @PathVariable id: Int,
        @RequestBody request: ObjectItemUpdateRequest,
    ): ResponseEntity<Response> {
        accessService.ensureCanManage(user, id)
        val item = objectItemService.update(id, request)
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
            hasControlPassword = item.hasControlPassword,
        )

        return builder.ok().data(rs).build()
    }

    @PutMapping("/batch")
    fun updateBatch(
        @AuthenticationPrincipal user: LoginUser,
        @RequestBody request: ObjectItemBatchUpdateRequest,
    ): ResponseEntity<Response> {
        request.items.forEach { it.id?.let { id -> accessService.ensureCanManage(user, id) } }
        val items = objectItemService.updateBatch(request.items)

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
                hasControlPassword = it.hasControlPassword,
            )
        }

        return builder.ok().data(rs).build()
    }

    @DeleteMapping("/batch")
    fun deleteBatch(
        @AuthenticationPrincipal user: LoginUser,
        @RequestBody request: ObjectItemBatchDeleteRequest,
    ): ResponseEntity<Response> {
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
        @RequestParam(required = false) status: ObjectItemUpdateStatus?,
    ): ResponseEntity<Response> {
        val updates = objectItemUpdateService.findByObjectItem(id, status)

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

        val rs = updates.map {
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

        return builder.ok().data(rs).build()
    }

    @GetMapping("/{id}/comments")
    fun listComments(
        @PathVariable id: Int,
        @RequestParam(required = false) status: ObjectItemCommentStatus?,
    ): ResponseEntity<Response> {
        val comments = objectItemCommentService.findByObjectItem(id, status)

        data class Response(
            val id: Int?,
            val objectItemId: Int?,
            val nickName: String?,
            val content: String?,
            val status: ObjectItemCommentStatus?,
            val createTime: LocalDateTime?,
            val updateTime: LocalDateTime?,
        )

        val rs = comments.map {
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

        return builder.ok().data(rs).build()
    }

    @PostMapping("/{id}/comments")
    fun createComment(
        @PathVariable id: Int,
        @RequestBody request: ObjectItemCommentSaveRequest,
    ): ResponseEntity<Response> {
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
        @RequestBody request: JoinApplicationSaveRequest,
    ): ResponseEntity<Response> {
        val application = joinApplicationService.create(id, request)

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
        )

        return builder.ok().data(rs).build()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SORT = "id,desc"
    }
}
