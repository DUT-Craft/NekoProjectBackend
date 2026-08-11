package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "idea_likes")
class IdeaLike(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var ideaId: Long = 0,
    @Column(nullable = false)
    var userId: Long = 0,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
)
