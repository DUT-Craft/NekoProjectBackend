package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.Instant

/**
 * 审计日志（落库，设计 §11 / §13）：替代原 `OperationLogService` 仅写文件、不可查询的形态。
 * 按时间、操作者、动作建索引，支撑管理端分页查询。
 *
 * 约束：不记录密码 / 验证码 / 完整令牌明文（仅记摘要 description）。
 */
@Entity
@Table(
    name = "audit_log",
    indexes = [
        Index(name = "idx_audit_time", columnList = "time"),
        Index(name = "idx_audit_operator", columnList = "operator_id"),
        Index(name = "idx_audit_action", columnList = "action"),
        Index(name = "idx_audit_target", columnList = "target_type,target_id"),
    ],
)
class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "time", nullable = false)
    var time: Instant = Instant.now()

    @Column(name = "operator_id")
    var operatorId: Long? = null

    @Column(name = "operator_name", length = 64)
    var operatorName: String? = null

    @Column(name = "operator_role", length = 16)
    var operatorRole: String? = null

    @Column(name = "action", nullable = false, length = 64)
    var action: String = ""

    @Column(name = "target_type", length = 32)
    var targetType: String? = null

    @Column(name = "target_id", length = 64)
    var targetId: String? = null

    @Column(name = "description", length = 512)
    var description: String? = null

    @Column(name = "ip", length = 64)
    var ip: String? = null

    @Column(name = "success", nullable = false)
    var success: Boolean = true

    @Column(name = "error", length = 512)
    var error: String? = null
}
