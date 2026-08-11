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
@Table(name = "activities")
class Activity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 80)
    var slug: String = "",
    @Column(nullable = false, length = 140)
    var name: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var kind: ActivityKind = ActivityKind.LONG_TERM,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: ActivityStatus = ActivityStatus.ONGOING,
    @Column(nullable = false, length = 60)
    var statusLabel: String = "长期进行",
    @Column(nullable = false, length = 50)
    var serverSlug: String = "",
    @Column(nullable = false, length = 255)
    var timeText: String = "",
    @Column(nullable = false, length = 255)
    var participation: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var description: String = "",
    @Column(nullable = false, length = 60)
    var icon: String = "event",
    var iconMediaId: Long? = null,
    var coverMediaId: Long? = null,
    @Column(nullable = false, columnDefinition = "text")
    var contentBlocks: String = "[]",
    @Column(nullable = false)
    var requiresPack: Boolean = false,
    @Column(nullable = false)
    var priority: Int = 0,
    @Column(nullable = false)
    var published: Boolean = true,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
