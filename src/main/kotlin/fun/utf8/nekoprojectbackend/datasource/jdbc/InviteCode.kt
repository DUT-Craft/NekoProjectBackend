package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 项目管理注册邀请码：总管理生成、项目管理凭此注册。
 *
 * 持久化以便展示历史；一次性消费由 [fun.utf8.nekoprojectbackend.datasource.jdbc.InviteCodeRepository.consume]
 * 的原子 UPDATE（`status = UNUSED AND expires_at > now`）保证，不再依赖 Redis。
 */
@Entity
@Table(
    name = "invite_code",
    indexes = [Index(name = "idx_invite_code_code", columnList = "code", unique = true)],
)
class InviteCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "code", nullable = false, unique = true, length = 64)
    var code: String = "",

    /** 生成者（总管理）用户 ID。 */
    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: InviteCodeStatus? = InviteCodeStatus.UNUSED,

    /** 消费者（注册成功的项目管理）用户 ID；未使用为 null。 */
    @Column(name = "used_by")
    var usedBy: Long? = null,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,
)

/** 邀请码状态：未使用 / 已使用 / 已过期。 */
enum class InviteCodeStatus {
    UNUSED,

    USED,

    EXPIRED,
}
