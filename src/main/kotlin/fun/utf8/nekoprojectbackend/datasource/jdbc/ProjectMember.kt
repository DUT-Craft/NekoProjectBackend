package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 项目成员关系（设计 §2.3、§9）：把「谁可以管理这个项目」从 [ObjectItem.ownerId] 单字段
 * 升级为 (project, user, role, status) 关系表，支撑 OWNER / MANAGER / MEMBER 三级角色。
 *
 * - `(project_id, user_id)` 唯一约束：同一用户在同一项目只一条关系记录；
 * - 退出 / 移除走软状态（[MemberStatus.LEFT] / [MemberStatus.REMOVED]），保留审计链路，不物理删行；
 * - 迁移期与 [ObjectItem.ownerId] 双轨：[fun.utf8.nekoprojectbackend.service.AccessService] 同时识别两者。
 */
@Entity
@Table(
    name = "project_member",
    uniqueConstraints = [UniqueConstraint(name = "uk_pm_project_user", columnNames = ["project_id", "user_id"])],
    indexes = [
        Index(name = "idx_pm_user", columnList = "user_id"),
        Index(name = "idx_pm_project_role", columnList = "project_id,role"),
    ],
)
class ProjectMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "project_id", nullable = false)
    var projectId: Int? = null

    @Column(name = "user_id", nullable = false)
    var userId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    var role: ProjectRole? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: MemberStatus? = MemberStatus.ACTIVE

    /** 邀请/添加该成员的用户 ID；OWNER 初始行可为 null（自建项目）。 */
    @Column(name = "invited_by")
    var invitedBy: Long? = null

    @Column(name = "joined_at", nullable = false)
    var joinedAt: LocalDateTime? = null

    @Column(name = "left_at")
    var leftAt: LocalDateTime? = null

    @PrePersist
    fun prePersist() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now()
        }
    }
}

/** 项目内角色（设计 §2.3）：OWNER 全权 / MANAGER 被授权管理 / MEMBER 普通成员。 */
enum class ProjectRole {
    OWNER,
    MANAGER,
    MEMBER,
}

/** 成员关系状态：活跃 / 已主动退出 / 已被移除（软状态，保留审计）。 */
enum class MemberStatus {
    ACTIVE,
    LEFT,
    REMOVED,
}
