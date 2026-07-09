package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*

/** 用户实体。 */
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "username", nullable = false, unique = true, length = 64)
    var username: String,

    @Column(name = "password", nullable = false, length = 255)
    var password: String,

    @Column(name = "email", nullable = false, unique = true, length = 128)
    var email: String,

    @Column(name = "nickname", nullable = false, length = 64)
    var nickname: String = "Neko",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: Status? = Status.ACTIVE,

)

/** 账号状态：正常 / 封禁。 */
enum class Status {
    ACTIVE,

    BANNED
}
