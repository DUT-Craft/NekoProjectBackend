package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 用户表数据访问层。 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?

    /** 大小写不敏感查找（按归一化列 [User.usernameLower]）。 */
    fun findByUsernameLower(usernameLower: String): User?

    fun findByEmail(email: String): User?
    fun findByRole(role: Role): List<User>

    /** 角色范围内的全部账号：用于列出可归属项目的账号（项目管理 + 总管理）。 */
    fun findByRoleIn(roles: Collection<Role>): List<User>

    /** 拥有项目创建资格或为超级管理员的账号：设计 §2.2 下「可创建/归属项目」的候选人。 */
    fun findByCanCreateProjectTrueOrRole(canCreateProject: Boolean, role: Role): List<User>

    /** 当前活跃的超级管理员数量：用于「最后一个超级管理员」保护（设计 §11）。 */
    fun countByRoleAndStatus(role: Role, status: Status): Long
}
