package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "history_items")
class HistoryItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 80)
    var slug: String = "",
    @Column(nullable = false, length = 160)
    var title: String = "",
    @Column(nullable = false, length = 255)
    var meta: String = "",
    @Column(length = 500)
    var imageUrl: String? = null,
    var coverMediaId: Long? = null,
    @Column(nullable = false, length = 255)
    var altText: String = "活动记录",
    @Column(nullable = false)
    var featured: Boolean = false,
    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var contentBlocks: String = "[]",
    var happenedAt: Instant? = null,
    @Column(nullable = false)
    var published: Boolean = true,
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
