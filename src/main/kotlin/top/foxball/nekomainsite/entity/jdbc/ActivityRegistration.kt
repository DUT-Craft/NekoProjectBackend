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
@Table(name = "activity_registrations")
class ActivityRegistration(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, length = 80)
    var activitySlug: String = "",
    var userId: Long? = null,
    @Column(nullable = false, length = 40)
    var minecraftId: String = "",
    @Column(nullable = false, length = 30)
    var qq: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: RegistrationStatus = RegistrationStatus.PENDING,
    @Column(columnDefinition = "text")
    var adminNote: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
