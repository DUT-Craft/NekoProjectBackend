package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.AuditLog
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.AuditLogRepository
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 操作记录系统：把用户的关键操作以 JSON Lines 追加写入文件（默认 `./logs/operation.log`）。
 *
 * - 异步：单线程后台写入，绝不阻塞请求线程；
 * - 上下文同步抓取：operator（SecurityContext）/ ip（RequestContextHolder）必须在请求线程上取，
 *   提交到异步线程后这些 ThreadLocal 已失效，故 [record] 内先抓取再提交写盘；
 * - 滚动：单文件超过 `neko.audit.max-size-mb` 时滚动为 `operation-{ts}.log`，并保留最近 `max-archives` 份；
 * - 失败不传播：写盘异常仅记到应用日志，绝不影响业务调用（审计是 best-effort）。
 *
 * 记录字段：time / operatorId / operatorName / operatorRole / action / targetType / targetId /
 * description / ip / success / error。建议在控制器层调用，以便自动抓取登录用户与来源 IP。
 */
@Service
class OperationLogService(
    private val auditLogRepository: AuditLogRepository,
    @Value("\${neko.audit.log-path:./logs/operation.log}") private val logPath: String,
    @Value("\${neko.audit.max-size-mb:50}") private val maxSizeMb: Long,
    @Value("\${neko.audit.max-archives:30}") private val maxArchives: Int,
    @Value("\${neko.audit.enabled:true}") private val enabled: Boolean,
    @Value("\${neko.audit.trust-forwarded:false}") private val trustForwarded: Boolean,
) {
    private val appLog = LoggerFactory.getLogger(javaClass)
    private val maxBytes: Long = maxSizeMb.coerceAtLeast(1) * 1024L * 1024L
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "operation-log-writer").apply { isDaemon = true }
    }

    @PostConstruct
    fun init() {
        runCatching { File(logPath).parentFile?.takeIf { !it.exists() }?.mkdirs() }
            .onFailure { appLog.error("创建操作日志目录失败: ${it.message}", it) }
    }

    @PreDestroy
    fun shutdown() {
        writer.shutdown()
        runCatching { writer.awaitTermination(10, TimeUnit.SECONDS) }
    }

    /**
     * 记录一条操作。operator 字段缺省时自动从 SecurityContext 取当前登录用户；ip 自动取当前请求。
     * 失败操作（success=false）也会记录，便于安全审计；但调用方需自行 try/catch 后再调用并继续抛出异常。
     */
    fun record(
        action: String,
        targetType: String? = null,
        targetId: Any? = null,
        description: String,
        success: Boolean = true,
        error: String? = null,
        operatorId: Long? = null,
        operatorName: String? = null,
        operatorRole: String? = null,
    ) {
        if (!enabled) {
            return
        }
        // 上下文必须在请求线程同步抓取（异步线程上 ThreadLocal 已失效）
        val (opId, opName, opRole) = resolveOperator(operatorId, operatorName, operatorRole)
        val ip = currentIp()
        val line = buildJson(opId, opName, opRole, action, targetType, targetId, description, ip, success, error)
        // 双写：异步写文件（兜底）+ 异步落库（可查询，设计 §11 / §13）
        writer.execute {
            runCatching { appendLine(line) }
                .onFailure { appLog.error("写入操作日志失败: ${it.message}", it) }
        }
        writer.execute {
            runCatching { persistAudit(opId, opName, opRole, action, targetType, targetId, description, ip, success, error) }
                .onFailure { appLog.warn("审计落库失败: ${it.message}") }
        }
    }

    private fun persistAudit(
        operatorId: Long?, operatorName: String?, operatorRole: String?,
        action: String, targetType: String?, targetId: Any?, description: String,
        ip: String?, success: Boolean, error: String?,
    ) {
        auditLogRepository.save(
            AuditLog().apply {
                this.operatorId = operatorId
                this.operatorName = operatorName
                this.operatorRole = operatorRole
                this.action = action
                this.targetType = targetType
                this.targetId = targetId?.toString()
                this.description = description.take(512)
                this.ip = ip
                this.success = success
                this.error = error?.take(512)
            }
        )
    }

    /** 便捷重载：直接传已登录的 [LoginUser]。 */
    fun record(
        operator: LoginUser?,
        action: String,
        targetType: String? = null,
        targetId: Any? = null,
        description: String,
        success: Boolean = true,
        error: String? = null,
    ) = record(
        action = action,
        targetType = targetType,
        targetId = targetId,
        description = description,
        success = success,
        error = error,
        operatorId = operator?.id,
        operatorName = operator?.username,
        operatorRole = operator?.role?.name,
    )

    /** 未显式传 operator 时，回退到当前 SecurityContext 的登录主体（公网匿名操作为 null）。 */
    private fun resolveOperator(id: Long?, name: String?, role: String?): Triple<Long?, String?, String?> {
        if (id != null || name != null) {
            return Triple(id, name, role)
        }
        val principal = runCatching { SecurityContextHolder.getContext().authentication?.principal }.getOrNull()
        val user = principal as? LoginUser
        return Triple(user?.id, user?.username, user?.role?.name)
    }

    private fun currentIp(): String? {
        val attrs = runCatching {
            RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        }.getOrNull() ?: return null
        val request = attrs.request
        // 仅在部署于可信反向代理后端时才采信 X-Forwarded-For / X-Real-IP：
        // 这些头客户端可随意伪造，未部署反代时直接信任会让攻击者注入任意假 IP 污染审计日志。
        // neko.audit.trust-forwarded 默认 false，部署在 Nginx/网关后才开启。
        if (trustForwarded) {
            request.getHeader("X-Forwarded-For")?.let {
                val first = it.substringBefore(',').trim()
                if (first.isNotEmpty()) return first
            }
            request.getHeader("X-Real-IP")?.let { if (it.isNotBlank()) return it.trim() }
        }
        return request.remoteAddr
    }

    @Synchronized
    private fun appendLine(line: String) {
        val file = File(logPath)
        if (file.exists() && file.length() > maxBytes) {
            roll(file)
        }
        Files.write(
            file.toPath(),
            (line + System.lineSeparator()).toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun roll(file: File) {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val archive = File(file.parentFile, "${file.nameWithoutExtension}-$ts.log")
        file.renameTo(archive)
        val base = file.nameWithoutExtension
        val archives = file.parentFile
            .listFiles { f -> f.isFile && f.name.startsWith("$base-") && f.name.endsWith(".log") }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
        if (archives.size > maxArchives) {
            archives.take(archives.size - maxArchives).forEach { it.delete() }
        }
    }

    private fun buildJson(
        operatorId: Long?, operatorName: String?, operatorRole: String?,
        action: String, targetType: String?, targetId: Any?, description: String,
        ip: String?, success: Boolean, error: String?,
    ): String {
        val sb = StringBuilder(256)
        sb.append("{\"time\":\"").append(escape(Instant.now().toString())).append('"')
        sb.raw("operatorId", operatorId)
        sb.str("operatorName", operatorName)
        sb.str("operatorRole", operatorRole)
        sb.str("action", action)
        sb.str("targetType", targetType)
        sb.str("targetId", targetId?.toString())
        sb.str("description", description)
        sb.str("ip", ip)
        sb.bool("success", success)
        sb.str("error", error)
        sb.append('}')
        return sb.toString()
    }

    private fun StringBuilder.str(key: String, value: String?) {
        append(',').append('"').append(key).append("\":")
        if (value == null) {
            append("null")
        } else {
            append('"').append(escape(value)).append('"')
        }
    }

    private fun StringBuilder.raw(key: String, value: Any?) {
        append(',').append('"').append(key).append("\":").append(value?.toString() ?: "null")
    }

    private fun StringBuilder.bool(key: String, value: Boolean) {
        append(',').append('"').append(key).append("\":").append(value)
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
