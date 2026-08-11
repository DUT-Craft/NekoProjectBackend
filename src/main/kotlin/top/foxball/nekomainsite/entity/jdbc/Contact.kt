package top.foxball.nekomainsite.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "contacts")
class Contact(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 60)
    var slug: String = "",
    @Column(nullable = false, length = 80)
    var name: String = "",
    @Column(nullable = false, length = 160)
    var contact: String = "",
    @Column(nullable = false, length = 255)
    var responsibilities: String = "",
    @Column(nullable = false)
    var published: Boolean = true,
    @Column(nullable = false)
    var sortOrder: Int = 0,
)
