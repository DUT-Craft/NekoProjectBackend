package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.service.TagService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 公开标签字典接口（/api/project/tags）：匿名可读，仅返回活跃节点。
 * 驱动投稿 / 项目编辑 / 项目墙的 Cascader 与远程搜索。
 */
@RestController
@RequestMapping("/api/project/tags")
class TagController(
    private val tagService: TagService,
    private val builder: ResponseBuilder,
) {
    /** Tag 树（父子层级），供 Cascader 预设。 */
    @GetMapping("/tree")
    fun tree(): ResponseEntity<Response> = builder.ok().data(tagService.getTree()).build()

    /** 扁平搜索：仅可选活跃节点，按名称命中。 */
    @GetMapping
    fun search(@RequestParam(required = false) keyword: String?): ResponseEntity<Response> =
        builder.ok().data(tagService.search(keyword)).build()
}
