package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.TagSaveRequest
import `fun`.utf8.nekoprojectbackend.service.TagService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 总管理标签字典维护接口（/api/admin/tags）：JWT 鉴权，仅总管理可写。
 * Tag 是影响全站项目的全局配置，权限口径与邀请码 / 项目分配一致（[AccessService.requireSuperAdmin]）。
 * 项目管理可经公开 GET 接口（/api/project/tags）读取并在名下项目选择，但无维护权限。
 */
@RestController
@RequestMapping("/api/admin/tags")
class AdminTagController(
    private val tagService: TagService,
    private val accessService: AccessService,
    private val operationLogService: OperationLogService,
    private val builder: ResponseBuilder,
) {
    /** 完整列表（含已删除），每个 Tag 附关联项目数；平铺返回，前端按 parentId 组树。 */
    @GetMapping
    fun list(@AuthenticationPrincipal admin: LoginUser): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        return builder.ok().data(tagService.listForAdmin()).build()
    }

    @PostMapping
    fun create(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestBody request: TagSaveRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val tag = tagService.create(request)
        operationLogService.record(
            operator = admin,
            action = "TAG_CREATE",
            targetType = "TAG",
            targetId = tag.id,
            description = "新增标签「${tag.name}」",
        )
        return builder.ok().data(tag).build()
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
        @RequestBody request: TagSaveRequest,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val tag = tagService.update(id, request)
        operationLogService.record(
            operator = admin,
            action = "TAG_UPDATE",
            targetType = "TAG",
            targetId = tag.id,
            description = "更新标签 #$id「${tag.name}」",
        )
        return builder.ok().data(tag).build()
    }

    /** 软删除并解除项目关联；返回受影响项目数。存在活跃子节点时返回 409。 */
    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal admin: LoginUser,
        @PathVariable id: Long,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val affected = tagService.delete(id)
        operationLogService.record(
            operator = admin,
            action = "TAG_DELETE",
            targetType = "TAG",
            targetId = id,
            description = "删除标签 #$id（解除 $affected 个项目关联）",
        )

        data class DeleteResult(val id: Long, val affectedProjects: Long)
        return builder.ok().data(DeleteResult(id = id, affectedProjects = affected)).build()
    }
}
