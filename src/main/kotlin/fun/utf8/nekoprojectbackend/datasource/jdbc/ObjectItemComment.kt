package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/** 项目评论实体：对某项目条目的公开评论，需审核。 */
@Entity
@Table(
    name = "object_item_comment",
    indexes = [
        Index(name = "idx_object_item_comment_item_id", columnList = "object_item_id"),
        Index(name = "idx_object_item_comment_status", columnList = "status"),
    ],
)
class ObjectItemComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "object_item_id", nullable = false)
    var objectItemId: Int? = null

    /** 作者用户 ID（users.id）；匿名提交的历史行可为 null（标记为「历史匿名」，设计 §14.12）。 */
    @Column(name = "author_user_id")
    var authorUserId: Long? = null

    @Column(name = "nick_name", length = 64)
    var nickName: String? = null

    @Lob
    @Column(name = "content", nullable = false)
    var content: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ObjectItemCommentStatus? = ObjectItemCommentStatus.PENDING

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

/** 评论审核状态：待审核 / 通过 / 拒绝 / 已删除。 */
enum class ObjectItemCommentStatus {
    PENDING,

    APPROVED,

    REJECTED,

    DELETED,
}
