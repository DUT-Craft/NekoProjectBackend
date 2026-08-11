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
@Table(name = "servers")
class Server(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 50)
    var slug: String = "",
    @Column(nullable = false, length = 100)
    var name: String = "",
    @Column(nullable = false, length = 160)
    var gameplay: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var category: ServerCategory = ServerCategory.PERMANENT,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    var status: ServerStatus = ServerStatus.OFFLINE,
    @Column(nullable = false, length = 60)
    var statusLabel: String = "离线",
    @Column(nullable = false)
    var onlineCount: Int = 0,
    @Column(nullable = false)
    var capacity: Int = 0,
    @Column(nullable = false, length = 255)
    var address: String = "",
    @Column(nullable = false, length = 80)
    var version: String = "",
    @Column(nullable = false, length = 255)
    var pack: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var description: String = "",
    @Column(nullable = false, columnDefinition = "text")
    var rules: String = "",
    @Column(nullable = false, length = 60)
    var icon: String = "server",
    var iconMediaId: Long? = null,
    @Column(nullable = false)
    var featured: Boolean = false,
    @Column(nullable = false)
    var published: Boolean = true,
    @Column(nullable = false)
    var maintenance: Boolean = false,
    var lastCheckedAt: Instant? = null,
    @Column(length = 255)
    var statusError: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
