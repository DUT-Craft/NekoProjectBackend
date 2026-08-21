package top.foxball.nekomainsite.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.nekomainsite.authentication.SubmissionRateLimiter
import top.foxball.nekomainsite.entity.jdbc.ApplicationKind
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.handlder.TooManyRequestsException
import top.foxball.nekomainsite.service.ActivityView
import top.foxball.nekomainsite.service.ApplicationCommand
import top.foxball.nekomainsite.service.FeedbackCommand
import top.foxball.nekomainsite.service.IdeaCommand
import top.foxball.nekomainsite.service.RegistrationCommand
import top.foxball.nekomainsite.service.SiteContentService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api/public")
class PublicContentController(
    private val siteContentService: SiteContentService,
    private val submissionRateLimiter: SubmissionRateLimiter,
    private val builder: ResponseBuilder,
) {
    @GetMapping("/home")
    fun home(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.home()).build()

    @GetMapping("/servers")
    fun servers(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.servers()).build()

    @GetMapping("/servers/{slug}")
    fun server(@PathVariable slug: String): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.server(slug)).build()

    @GetMapping("/activities")
    fun activities(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.activities()).build()

    @GetMapping("/activities/{slug}")
    fun activity(@PathVariable slug: String): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.activity(slug)).build()

    @GetMapping("/announcements")
    fun announcements(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.announcements()).build()

    @GetMapping("/announcements/{slug}")
    fun announcement(@PathVariable slug: String): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.announcement(slug)).build()

    @GetMapping("/contacts")
    fun contacts(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.contacts()).build()

    @GetMapping("/wiki")
    fun wiki(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.wiki()).build()

    @GetMapping("/wiki/{slug}")
    fun wiki(@PathVariable slug: String): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.wiki(slug)).build()

    @GetMapping("/history")
    fun history(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.history()).build()

    @GetMapping("/history/{slug}")
    fun history(@PathVariable slug: String): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.history(slug)).build()

    @GetMapping("/ideas")
    fun ideas(): ResponseEntity<ApiResponse> = builder.ok().data(siteContentService.ideas()).build()

    @GetMapping("/applications")
    fun applications(): ResponseEntity<ApiResponse> = builder.ok().data(mapOf("submissionRequiresLogin" to true)).build()

    @GetMapping("/applications/mine")
    fun myApplications(authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(siteContentService.memberApplications(authentication.userId())).build()

    @PostMapping("/applications")
    fun submitApplication(
        authentication: Authentication?,
        @Valid @RequestBody request: ApplicationRequest,
    ): ResponseEntity<ApiResponse> {
        ensureAllowed(authentication, "applications")
        val id = siteContentService.submitApplication(authentication.userId(), request.toCommand())
        return builder.ok().data(mapOf("id" to id, "status" to "PENDING")).build()
    }

    @PostMapping("/ideas")
    fun submitIdea(authentication: Authentication?, @Valid @RequestBody request: IdeaRequest): ResponseEntity<ApiResponse> {
        ensureAllowed(authentication, "ideas")
        val id = siteContentService.submitIdea(authentication.userId(), IdeaCommand(request.title, request.category, request.description))
        return builder.ok().data(mapOf("id" to id, "status" to "PENDING")).build()
    }

    @PostMapping("/ideas/{id}/like")
    fun likeIdea(authentication: Authentication?, @PathVariable id: Long): ResponseEntity<ApiResponse> {
        ensureAllowed(authentication, "likes", limit = LIKE_LIMIT)
        val likes = siteContentService.likeIdea(authentication.userId(), id)
        return builder.ok().data(mapOf("id" to id, "likes" to likes)).build()
    }

    @PostMapping("/activities/{slug}/registrations")
    fun registerActivity(
        authentication: Authentication?,
        @PathVariable slug: String,
        @Valid @RequestBody request: RegistrationRequest,
    ): ResponseEntity<ApiResponse> {
        ensureAllowed(authentication, "registrations")
        val id = siteContentService.registerActivity(authentication.userId(), slug, RegistrationCommand(request.minecraftId, request.qq))
        return builder.ok().data(mapOf("id" to id, "status" to "PENDING")).build()
    }

    @PostMapping("/feedback")
    fun submitFeedback(authentication: Authentication?, @Valid @RequestBody request: FeedbackRequest): ResponseEntity<ApiResponse> {
        ensureAllowed(authentication, "feedback")
        val id = siteContentService.submitFeedback(authentication.userId(), FeedbackCommand(request.body))
        return builder.ok().data(mapOf("id" to id, "status" to "OPEN")).build()
    }

    private fun ensureAllowed(authentication: Authentication?, resource: String, limit: Int = SUBMISSION_LIMIT) {
        val userId = authentication?.principal as? Long ?: return
        val key = "$userId|$resource"
        if (!submissionRateLimiter.allow(key, limit, WINDOW_MS)) {
            throw TooManyRequestsException("操作过于频繁，请稍后再试")
        }
    }

    private fun Authentication?.userId(): Long? = (this?.principal as? Long)

    private companion object {
        const val SUBMISSION_LIMIT = 5
        const val LIKE_LIMIT = 30
        const val WINDOW_MS = 10 * 60 * 1000L
    }

    data class ApplicationRequest(
        @field:NotBlank @field:Size(max = 30) val kind: String,
        @field:NotBlank @field:Size(max = 80) val name: String,
        @field:Size(max = 80) val studentId: String? = null,
        @field:NotBlank @field:Pattern(regexp = "\\d{5,12}") val qq: String,
        @field:Size(max = 40) val minecraftId: String? = null,
        @field:Size(max = 5000) val reason: String? = null,
        @field:Size(max = 40) val participantCount: String? = null,
        @field:Size(max = 120) val purpose: String? = null,
        @field:Size(max = 120) val expectedTime: String? = null,
        @field:Size(max = 5000) val requirements: String? = null,
        @field:Size(max = 120) val availableTime: String? = null,
        @field:Size(max = 120) val skill: String? = null,
    ) {
        fun toCommand(): ApplicationCommand = ApplicationCommand(
            kind = runCatching { ApplicationKind.valueOf(kind.trim().uppercase()) }
                .getOrElse { throw ParamErrorException("申请类型不正确") },
            name = name, studentId = studentId, qq = qq, minecraftId = minecraftId, reason = reason,
            participantCount = participantCount, purpose = purpose, expectedTime = expectedTime,
            requirements = requirements, availableTime = availableTime, skill = skill,
        )
    }

    data class IdeaRequest(
        @field:NotBlank @field:Size(max = 180) val title: String,
        @field:NotBlank @field:Size(max = 40) val category: String,
        @field:NotBlank @field:Size(max = 5000) val description: String,
    )

    data class RegistrationRequest(
        @field:NotBlank @field:Size(max = 40) val minecraftId: String,
        @field:NotBlank @field:Pattern(regexp = "\\d{5,12}") val qq: String,
    )

    data class FeedbackRequest(@field:NotBlank @field:Size(max = 5000) val body: String)
}
