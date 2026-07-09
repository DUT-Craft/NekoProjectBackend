package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/** 想法（Mind）实体：用户提交的创意/想法，含标题、正文、MC ID 及审核状态。 */
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

/** 想法审核状态：待审核 / 通过 / 拒绝 / 已删除。 */
enum class MindStatus {
    PENDING,

    APPROVED,

    REJECTED,

    DELETED,
}
