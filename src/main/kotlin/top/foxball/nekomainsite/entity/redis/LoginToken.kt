package top.foxball.nekomainsite.entity.redis

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed

@RedisHash(value = "login_token", timeToLive = 259200)
class LoginToken {
    @Id
    var id: String? = null

    @Indexed
    var userId: Long? = null

    var userAgent: String? = null

    @TimeToLive
    var ttlSeconds: Long = 259200

    constructor()

    constructor(id: String, userId: Long, userAgent: String, ttlSeconds: Long = 259200) {
        this.id = id
        this.userId = userId
        this.userAgent = userAgent
        this.ttlSeconds = ttlSeconds
    }
}
