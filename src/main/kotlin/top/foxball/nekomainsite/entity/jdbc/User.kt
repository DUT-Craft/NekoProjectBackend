package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    var username: String = "",

    @Column(nullable = false, unique = true, length = 100)
    var email: String = "",

    @Column(nullable = false, length = 255)
    var password: String = "",

    @Column(nullable = false, length = 30)
    var role: String = "USER",

    @Column(nullable = false, length = 30)
    var authSource: String = "LOCAL",

    @Column(length = 100)
    var externalUserId: String? = null,

    @Column(length = 80)
    var displayName: String? = null,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
