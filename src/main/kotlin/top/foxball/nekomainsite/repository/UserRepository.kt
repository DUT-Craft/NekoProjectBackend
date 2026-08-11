package top.foxball.nekomainsite.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.nekomainsite.entity.jdbc.User

interface UserRepository : JpaRepository<User, Long> {
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findByExternalUserId(externalUserId: String): User?
    fun countByRoleAndEnabledTrue(role: String): Long
}
