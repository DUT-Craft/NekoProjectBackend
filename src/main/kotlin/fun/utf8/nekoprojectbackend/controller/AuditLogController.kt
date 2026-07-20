package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.shared.Response
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * 审计日志查询接口（/api/admin/audit-logs，设计 §11 / §13）：仅总管理可查询。
 * 支持 action / targetType / operatorId / 时间区间过滤 + 分页。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditLogController(
    private val auditLogRepository: `fun`.utf8.nekoprojectbackend.datasource.jdbc.AuditLogRepository,
    private val accessService: AccessService,
    private val builder: ResponseBuilder,
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal admin: LoginUser,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) operatorId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromTime: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) toTime: Instant?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ResponseEntity<Response> {
        accessService.requireSuperAdmin(admin)
        val safeSize = size.coerceIn(1, 200)
        val safePage = page.coerceAtLeast(0)
        val result = auditLogRepository.query(
            action = action?.ifBlank { null },
            targetType = targetType?.ifBlank { null },
            operatorId = operatorId,
            fromTime = fromTime,
            toTime = toTime,
            pageable = PageRequest.of(safePage, safeSize),
        )

        data class AuditItem(
            val id: Long?,
            val time: Instant,
            val operatorId: Long?,
            val operatorName: String?,
            val operatorRole: String?,
            val action: String,
            val targetType: String?,
            val targetId: String?,
            val description: String?,
            val ip: String?,
            val success: Boolean,
            val error: String?,
        )

        val items = result.content.map {
            AuditItem(
                id = it.id,
                time = it.time,
                operatorId = it.operatorId,
                operatorName = it.operatorName,
                operatorRole = it.operatorRole,
                action = it.action,
                targetType = it.targetType,
                targetId = it.targetId,
                description = it.description,
                ip = it.ip,
                success = it.success,
                error = it.error,
            )
        }

        data class AuditPage(
            val list: List<AuditItem>,
            val total: Long,
            val page: Int,
            val size: Int,
        )

        val rs = AuditPage(
            list = items,
            total = result.totalElements,
            page = safePage,
            size = safeSize,
        )
        return builder.ok().data(rs).build()
    }
}
