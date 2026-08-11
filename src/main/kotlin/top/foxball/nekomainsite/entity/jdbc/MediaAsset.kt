package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "media_assets")
class MediaAsset(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = 36)
    var draftId: String? = null,
    @Column(nullable = false, length = 30)
    var resourceType: String = "",
    var resourceId: Long? = null,
    @Column(nullable = false, length = 30)
    var purpose: String = "CONTENT",
    @Column(nullable = false, length = 255)
    var originalName: String = "image",
    @Column(nullable = false, unique = true, length = 255)
    var storageName: String = "",
    @Column(nullable = false, length = 100)
    var mimeType: String = "image/png",
    @Column(nullable = false)
    var sizeBytes: Long = 0,
    var width: Int? = null,
    var height: Int? = null,
    @Column(length = 255)
    var altText: String? = null,
    @Column(length = 500)
    var caption: String? = null,
    var deletedAt: Instant? = null,
    var createdBy: Long? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
)
