package top.foxball.nekomainsite.repository

import org.springframework.data.repository.CrudRepository
import org.springframework.context.annotation.Profile
import top.foxball.nekomainsite.entity.redis.LoginToken

@Profile("!local")
interface LoginTokenRepository: CrudRepository<LoginToken, String> {
    fun findByUserId(userId: Long?): MutableList<LoginToken?>?
}
