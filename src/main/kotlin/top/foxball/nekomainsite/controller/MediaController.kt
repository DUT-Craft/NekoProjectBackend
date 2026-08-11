package top.foxball.nekomainsite.controller

import org.springframework.core.io.ByteArrayResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import top.foxball.nekomainsite.service.MediaService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api")
class MediaController(
    private val mediaService: MediaService,
    private val builder: ResponseBuilder,
) {
    @PostMapping("/admin/content/drafts/{draftId}/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDraftImage(
        @PathVariable draftId: String,
        @RequestParam purpose: String,
        @RequestParam(required = false) altText: String?,
        @RequestParam(required = false) caption: String?,
        @RequestPart("file") file: MultipartFile,
        authentication: Authentication?,
    ): ResponseEntity<ApiResponse> = builder.ok().data(
        mediaService.uploadDraftImage(draftId, purpose, altText, caption, file, authentication.userId()),
    ).build()

    @PatchMapping("/admin/media/{id}")
    fun updateImage(
        @PathVariable id: Long,
        @RequestParam(required = false) altText: String?,
        @RequestParam(required = false) caption: String?,
        authentication: Authentication?,
    ): ResponseEntity<ApiResponse> = builder.ok().data(
        mediaService.updateImage(id, altText, caption, authentication.userId()),
    ).build()

    @DeleteMapping("/admin/media/{id}")
    fun deleteImage(@PathVariable id: Long, authentication: Authentication?): ResponseEntity<ApiResponse> =
        builder.ok().data(mediaService.deleteImage(id, authentication.userId())).build()

    @GetMapping("/public/media/{filename}")
    fun readImage(
        @PathVariable filename: String,
        authentication: Authentication?,
    ): ResponseEntity<ByteArrayResource> {
        val resource = mediaService.readImage(filename, authentication.isAdmin())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resource.mimeType))
            .cacheControl(CacheControl.noCache())
            .body(ByteArrayResource(resource.bytes))
    }

    private fun Authentication?.userId(): Long? = this?.principal as? Long
    private fun Authentication?.isAdmin(): Boolean =
        this?.authorities?.any { authority -> authority.authority == "ROLE_ADMIN" } == true
}
