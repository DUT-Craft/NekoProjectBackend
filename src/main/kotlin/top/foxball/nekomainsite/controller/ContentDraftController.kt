package top.foxball.nekomainsite.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import top.foxball.nekomainsite.service.ContentManagementService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api/admin/content")
class ContentDraftController(
    private val contentManagementService: ContentManagementService,
    private val builder: ResponseBuilder,
) {
    @GetMapping("/{resource}")
    fun list(@PathVariable resource: String): ResponseEntity<ApiResponse> = builder.ok().data(contentManagementService.list(resource)).build()

    @PostMapping("/{resource}/drafts")
    fun create(@PathVariable resource: String, authentication: Authentication?, @RequestBody request: CreateDraftRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.createDraft(resource, request.resourceId, authentication.userId())).build()

    @PostMapping("/{resource}/{id}/draft")
    fun edit(@PathVariable resource: String, @PathVariable id: Long, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.createDraft(resource, id, authentication.userId())).build()

    @GetMapping("/drafts/{draftId}")
    fun draft(@PathVariable draftId: String): ResponseEntity<ApiResponse> = builder.ok().data(contentManagementService.draft(draftId)).build()

    @PutMapping("/drafts/{draftId}")
    fun save(@PathVariable draftId: String, authentication: Authentication?, @Valid @RequestBody request: SaveDraftRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.saveDraft(draftId, request.version, request.payload, authentication.userId())).build()

    @PostMapping("/drafts/{draftId}/publish")
    fun publish(@PathVariable draftId: String, authentication: Authentication?, @Valid @RequestBody request: PublishRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.publish(draftId, request.version, authentication.userId())).build()

    @DeleteMapping("/drafts/{draftId}")
    fun delete(@PathVariable draftId: String, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.deleteDraft(draftId, authentication.userId())).build()

    @PostMapping("/{resource}/{id}/restore-previous")
    fun restorePrevious(@PathVariable resource: String, @PathVariable id: Long, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.restorePrevious(resource, id, authentication.userId())).build()

    @PostMapping("/{resource}/{id}/unpublish")
    fun unpublish(@PathVariable resource: String, @PathVariable id: Long, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.unpublish(resource, id, authentication.userId())).build()

    @DeleteMapping("/{resource}/{id}/permanent")
    fun deleteResource(@PathVariable resource: String, @PathVariable id: Long, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(contentManagementService.deleteResource(resource, id, authentication.userId())).build()

    data class CreateDraftRequest(val resourceId: Long? = null)
    data class SaveDraftRequest(@field:NotNull val version: Long, @field:NotNull val payload: JsonNode)
    data class PublishRequest(@field:NotNull val version: Long)
}

private fun Authentication?.userId(): Long? = (this?.principal as? Long)
