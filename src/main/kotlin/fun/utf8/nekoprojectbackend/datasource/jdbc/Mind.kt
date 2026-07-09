package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "mind",
    indexes = [
        Index(name = "idx_mind_status", columnList = "status"),
        Index(name = "idx_mind_mc_id", columnList = "mc_id"),
        Index(name = "idx_mind_title", columnList = "title"),
    ],
)
class Mind {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "title", nullable = false, length = 128)
    var title: String? = null

    @Column(name = "nick_name", length = 64)
    var nickName: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: MindStatus? = MindStatus.PENDING

    @Lob
    @Column(name = "content")
    var content: String? = null

    @Column(name = "mc_id", length = 64)
    var mcId: String? = null

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

enum class MindStatus {
    PENDING,

    APPROVED,

    REJECTED,

    DELETED,
}
