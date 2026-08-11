package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var operatorUserId: Long = 0,
    @Column(nullable = false, length = 40)
    var resourceType: String = "",
    @Column(nullable = false, length = 80)
    var resourceId: String = "",
    @Column(nullable = false, length = 40)
    var action: String = "",
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
)
