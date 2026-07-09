package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "join_application",
    indexes = [
        Index(name = "idx_join_application_item_id", columnList = "object_item_id"),
        Index(name = "idx_join_application_status", columnList = "status"),
    ],
)
class JoinApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "object_item_id", nullable = false)
    var objectItemId: Int? = null

    @Column(name = "nick_name", nullable = false, length = 64)
    var nickName: String? = null

    @Column(name = "mc_id", nullable = false, length = 64)
    var mcId: String? = null

    @Column(name = "contact", nullable = false, length = 255)
    var contact: String? = null

    @Lob
    @Column(name = "reason", nullable = false)
    var reason: String? = null

    @Column(name = "skill", length = 64)
    var skill: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: JoinApplicationStatus? = JoinApplicationStatus.PENDING

    @Column(name = "reject_reason", length = 255)
    var rejectReason: String? = null

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime? = null

    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime? = null

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createTime = createTime ?: now
        updateTime = updateTime ?: now
    }

    @PreUpdate
    fun preUpdate() {
        updateTime = LocalDateTime.now()
    }
}

enum class JoinApplicationStatus {
    PENDING,

    CONTACTED,

    ACCEPTED,

    REJECTED,
}
