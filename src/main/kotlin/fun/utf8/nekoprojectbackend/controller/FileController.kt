package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.FileCategory
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.FileService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/** 文件接口（/api/files）：上传、下载/预览、删除。 */
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService,
    private val builder: ResponseBuilder,
) {
    /** 上传：multipart/form-data，字段名 file，type 取 IMAGE|DOCUMENT。 */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam type: FileCategory,
        @RequestParam(required = false) objectItemId: Int?,
        @AuthenticationPrincipal user: LoginUser?,
    ): ResponseEntity<Response> {
        val result = fileService.upload(file, type, user, objectItemId)
        return builder.ok().data(result).build()
    }

    /** 我的文件列表（当前登录用户上传，分页）。需 JWT。 */
    @GetMapping("/mine")
    fun listMine(
        @AuthenticationPrincipal user: LoginUser,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ResponseEntity<Response> {
        val result = fileService.listByUploader(user, page, size)
        return builder.ok().data(result).build()
    }

    /** 下载/预览：inline=true 预览(图片)，默认下载(attachment)。 */
    @GetMapping("/{storedName:.+}")
    fun download(
        @PathVariable storedName: String,
        @RequestParam(required = false, defaultValue = "false") inline: Boolean,
        @AuthenticationPrincipal user: LoginUser?,
    ): ResponseEntity<InputStreamResource> {
        val (record, stream) = fileService.loadForDownload(storedName, user)
        val disposition = if (inline) "inline" else "attachment"
        val contentType = MediaType.parseMediaType(record.mimeType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE)
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "$disposition; filename=\"${record.originalName}\"")
            .contentLength(record.size ?: -1)
            .header("X-Content-Type-Options", "nosniff")
            .body(InputStreamResource(stream))
    }

    @DeleteMapping("/{storedName:.+}")
    fun delete(
        @PathVariable storedName: String,
        @AuthenticationPrincipal user: LoginUser,
    ): ResponseEntity<Response> {
        fileService.delete(storedName, user)
        return builder.ok().build()
    }
}
