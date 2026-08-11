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
@Table(name = "private_feedback")
class PrivateFeedback(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var userId: Long? = null,
    @Column(nullable = false, columnDefinition = "text")
    var body: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: FeedbackStatus = FeedbackStatus.OPEN,
    @Column(columnDefinition = "text")
    var adminNote: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
