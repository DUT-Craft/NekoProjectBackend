package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "announcements")
class Announcement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 80)
    var slug: String = "",
    @Column(nullable = false, length = 180)
    var title: String = "",
    @Column(nullable = false, length = 30)
    var category: String = "club",
    @Column(nullable = false, length = 80)
    var categoryLabel: String = "社团通知",
    @Column(nullable = false, length = 500)
    var summary: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",
    var coverMediaId: Long? = null,
    @Column(nullable = false, columnDefinition = "text")
    var contentBlocks: String = "[]",
    @Column(nullable = false)
    var priority: Int = 0,
    @Column(nullable = false)
    var pinned: Boolean = false,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: AnnouncementStatus = AnnouncementStatus.DRAFT,
    var publishedAt: Instant? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
