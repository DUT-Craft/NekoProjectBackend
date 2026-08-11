package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "wiki_sections")
class WikiSection(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 80)
    var slug: String = "",
    @Column(nullable = false, length = 140)
    var title: String = "",
    @Column(nullable = false, length = 255)
    var summary: String = "",
    @Column(nullable = false, length = 80)
    var groupName: String = "入服指南",
    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",
    @Column(nullable = false, length = 60)
    var icon: String = "guide",
    var iconMediaId: Long? = null,
    @Column(nullable = false, columnDefinition = "text")
    var contentBlocks: String = "[]",
    @Column(length = 500)
    var linkUrl: String? = null,
    @Column(nullable = false)
    var sortOrder: Int = 0,
    @Column(nullable = false)
    var published: Boolean = true,
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
