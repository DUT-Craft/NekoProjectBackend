package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 用户表数据访问层。 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findByRole(role: Role): List<User>

    /** 角色范围内的全部账号：用于列出可归属项目的账号（项目管理 + 总管理）。 */
    fun findByRoleIn(roles: Collection<Role>): List<User>
}