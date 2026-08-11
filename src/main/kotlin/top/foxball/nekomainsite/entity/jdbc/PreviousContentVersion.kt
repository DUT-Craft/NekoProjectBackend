package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(name = "previous_content_versions", uniqueConstraints = [UniqueConstraint(columnNames = ["resource_type", "resource_id"])])
class PreviousContentVersion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, length = 30)
    var resourceType: String = "",
    @Column(nullable = false)
    var resourceId: Long = 0,
    @Column(nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}",
    var savedBy: Long? = null,
    @Column(nullable = false)
    var savedAt: Instant = Instant.now(),
)
