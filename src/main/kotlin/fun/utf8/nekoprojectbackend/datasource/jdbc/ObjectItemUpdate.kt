package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/** 项目动态实体：项目条目下的进展更新公告，需审核。 */
@Entity
@Table(
    name = "object_item_update",
    indexes = [
        Index(name = "idx_object_item_update_item_id", columnList = "object_item_id"),
        Index(name = "idx_object_item_update_status", columnList = "status"),
    ],
)
class ObjectItemUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "object_item_id", nullable = false)
    var objectItemId: Int? = null

    @Column(name = "title", nullable = false, length = 128)
    var title: String? = null

    @Lob
    @Column(name = "content")
    var content: String? = null

    @Column(name = "image_url", length = 512)
    var imageUrl: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ObjectItemUpdateStatus? = ObjectItemUpdateStatus.PENDING

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

/** 动态审核状态：待审核 / 通过 / 拒绝 / 已删除。 */
enum class ObjectItemUpdateStatus {
    PENDING,

    APPROVED,

    REJECTED,

    DELETED,
}
