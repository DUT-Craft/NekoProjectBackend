package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 用户实体：账号基础信息 + 系统角色与状态 + 项目创建资格 + 生命周期时间戳。
 *
 * - [usernameLower] 为用户名小写归一化列，做大小写不敏感唯一性的数据库侧保障（[username] 列仍存原样展示值）；
 * - [email] 写入前已由 [fun.utf8.nekoprojectbackend.service.UserService] 小写化；
 * - 生命周期时间戳由 [prePersist] / [preUpdate] 自动维护；
 * - [emailVerifiedAt] 为空表示邮箱未验证（登录 / 写操作受限，见 AuthService.ensureLoginable）。
 */
@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "username", nullable = false, unique = true, length = 64)
    var username: String = ""

    /** 用户名小写归一化列：大小写不敏感唯一性的数据库侧保障，由 [prePersist]/[preUpdate] 维护。 */
    @Column(name = "username_lower", nullable = false, unique = true, length = 64)
    var usernameLower: String = ""

    @Column(name = "password", nullable = false, length = 255)
    var password: String = ""

    @Column(name = "email", nullable = false, unique = true, length = 128)
    var email: String = ""

    @Column(name = "nickname", nullable = false, length = 64)
    var nickname: String = "Neko"

    @Column(name = "avatar_url", length = 512)
    var avatarUrl: String? = null

    @Column(name = "bio", length = 500)
    var bio: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: Status = Status.ACTIVE

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    var role: Role = Role.USER

    /** 项目创建资格：与系统角色分离，USER 默认 false，凭邀请码或超管授权后置 true（设计 §2.2）。 */
    @Column(name = "can_create_project", nullable = false)
    var canCreateProject: Boolean = false

    @Column(name = "email_verified_at")
    var emailVerifiedAt: LocalDateTime? = null

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "deactivated_at")
    var deactivatedAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
        if (usernameLower.isBlank()) {
            usernameLower = username.trim().lowercase()
        }
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
        usernameLower = username.trim().lowercase()
    }
}

/**
 * 系统角色（设计 §2.1）。
 *
 * - [SUPER_ADMIN]：系统拥有者与维护者；
 * - [USER]：完成注册的普通用户，默认只能操作自己的资源与参与的项目；
 * - [PROJECT_MANAGER]：**仅作迁移过渡保留**，新代码一律用 [USER] + 项目内角色（[ProjectRole]）。
 *   阶段八确认无引用后删除。
 */
enum class Role {
    SUPER_ADMIN,
    USER,
    PROJECT_MANAGER,
}

/** 账号状态（设计 §10）：正常 / 封禁 / 停用 / 注销。 */
enum class Status {
    ACTIVE,
    BANNED,
    DEACTIVATED,
    DELETED,
}
