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
@Table(name = "applications")
class Application(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var userId: Long? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var kind: ApplicationKind = ApplicationKind.SKIN,
    @Column(nullable = false, length = 80)
    var name: String = "",
    @Column(length = 80)
    var studentId: String? = null,
    @Column(nullable = false, length = 30)
    var qq: String = "",
    @Column(length = 40)
    var minecraftId: String? = null,
    @Column(columnDefinition = "text")
    var reason: String? = null,
    @Column(length = 40)
    var participantCount: String? = null,
    @Column(length = 120)
    var purpose: String? = null,
    @Column(length = 120)
    var expectedTime: String? = null,
    @Column(columnDefinition = "text")
    var requirements: String? = null,
    @Column(length = 120)
    var availableTime: String? = null,
    @Column(length = 120)
    var skill: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: ModerationStatus = ModerationStatus.PENDING,
    @Column(columnDefinition = "text")
    var adminNote: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
