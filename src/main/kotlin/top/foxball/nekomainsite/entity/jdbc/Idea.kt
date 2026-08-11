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
@Table(name = "ideas")
class Idea(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var userId: Long? = null,
    @Column(nullable = false, length = 80)
    var nickname: String = "",
    @Column(nullable = false, length = 180)
    var title: String = "",
    @Column(nullable = false, length = 40)
    var category: String = "permanent",
    @Column(nullable = false, columnDefinition = "text")
    var description: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: ModerationStatus = ModerationStatus.PENDING,
    @Column(columnDefinition = "text")
    var publicReply: String? = null,
    @Column(length = 80)
    var relatedSlug: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
