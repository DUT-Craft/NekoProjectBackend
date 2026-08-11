package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_drafts")
class ContentDraft(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),
    @Column(nullable = false, length = 30)
    var resourceType: String = "",
    var resourceId: Long? = null,
    @Column(nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}",
    @Column(nullable = false)
    var version: Long = 0,
    @Version
    @Column(nullable = false)
    var lockVersion: Long = 0,
    var createdBy: Long? = null,
    var updatedBy: Long? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
