package top.foxball.nekomainsite.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.nekomainsite.entity.jdbc.FeedbackStatus
import top.foxball.nekomainsite.entity.jdbc.ModerationStatus
import top.foxball.nekomainsite.entity.jdbc.RegistrationStatus
import top.foxball.nekomainsite.service.AdminContactCommand
import top.foxball.nekomainsite.service.AdminContentService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api/admin")
class AdminContentController(
    private val adminContentService: AdminContentService,
    private val builder: ResponseBuilder,
) {
    @GetMapping("/overview")
    fun overview(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.overview()).build()

    @GetMapping("/applications")
    fun applications(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.applications()).build()

    @GetMapping("/ideas")
    fun ideas(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.ideas()).build()

    @GetMapping("/registrations")
    fun registrations(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.registrations()).build()

    @GetMapping("/feedback")
    fun feedback(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.feedback()).build()

    @PatchMapping("/ideas/{id}")
    fun updateIdea(authentication: Authentication?, @PathVariable id: Long, @Valid @RequestBody request: ModerationRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.updateIdea(authentication.operatorId(), id, request.status, request.note)).build()

    @PatchMapping("/applications/{id}")
    fun updateApplication(authentication: Authentication?, @PathVariable id: Long, @Valid @RequestBody request: ModerationRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.updateApplication(authentication.operatorId(), id, request.status, request.note)).build()

    @DeleteMapping("/applications/{id}")
    fun deleteApplication(authentication: Authentication?, @PathVariable id: Long): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.deleteApplication(authentication.operatorId(), id)).build()

    @PatchMapping("/feedback/{id}")
    fun updateFeedback(authentication: Authentication?, @PathVariable id: Long, @Valid @RequestBody request: FeedbackModerationRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.updateFeedback(authentication.operatorId(), id, request.status, request.note)).build()

    @PatchMapping("/registrations/{id}")
    fun updateRegistration(authentication: Authentication?, @PathVariable id: Long, @Valid @RequestBody request: RegistrationModerationRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.updateRegistration(authentication.operatorId(), id, request.status, request.note)).build()

    @PostMapping("/servers/{id}/refresh")
    fun refreshServer(authentication: Authentication?, @PathVariable id: Long): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.refreshServer(authentication.operatorId(), id)).build()

    @PatchMapping("/servers/{id}/maintenance")
    fun setServerMaintenance(
        authentication: Authentication?,
        @PathVariable id: Long,
        @Valid @RequestBody request: ServerMaintenanceRequest,
    ): ResponseEntity<ApiResponse> = builder.ok()
        .data(adminContentService.setServerMaintenance(authentication.operatorId(), id, request.maintenance))
        .build()

    @GetMapping("/contacts")
    fun contacts(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.contacts()).build()

    @PostMapping("/contacts")
    fun createContact(authentication: Authentication?, @Valid @RequestBody request: ContactRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.createContact(authentication.operatorId(), request.toCommand())).build()

    @PutMapping("/contacts/{id}")
    fun updateContact(authentication: Authentication?, @PathVariable id: Long, @Valid @RequestBody request: ContactRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.updateContact(authentication.operatorId(), id, request.toCommand())).build()

    @DeleteMapping("/contacts/{id}")
    fun hideContact(authentication: Authentication?, @PathVariable id: Long): ResponseEntity<ApiResponse> =
        builder.ok().data(adminContentService.hideContact(authentication.operatorId(), id)).build()

    @GetMapping("/audit-logs")
    fun auditLogs(): ResponseEntity<ApiResponse> = builder.ok().data(adminContentService.auditLogs()).build()

    data class ModerationRequest(
        val status: ModerationStatus,
        @field:Size(max = 5000) val note: String? = null,
    )

    data class FeedbackModerationRequest(
        val status: FeedbackStatus,
        @field:Size(max = 5000) val note: String? = null,
    )

    data class RegistrationModerationRequest(
        val status: RegistrationStatus,
        @field:Size(max = 5000) val note: String? = null,
    )

    data class ServerMaintenanceRequest(val maintenance: Boolean)

    data class ContactRequest(
        @field:NotBlank
        @field:Pattern(regexp = "[a-z0-9][a-z0-9-]{0,59}", message = "只能使用小写字母、数字和连字符")
        val slug: String,
        @field:NotBlank @field:Size(max = 80) val name: String,
        @field:NotBlank @field:Size(max = 160) val contact: String,
        @field:NotBlank @field:Size(max = 255) val responsibilities: String,
        val sortOrder: Int = 0,
        val published: Boolean = true,
    ) {
        fun toCommand() = AdminContactCommand(slug, name, contact, responsibilities, sortOrder, published)
    }

    private fun Authentication?.operatorId(): Long? = this?.principal as? Long
}
