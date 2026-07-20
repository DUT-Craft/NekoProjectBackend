package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 项目成员关系数据访问层。 */
@Repository
interface ProjectMemberRepository : JpaRepository<ProjectMember, Long> {
    /** 指定项目 + 用户 + 状态的关系记录（鉴权主入口用）。 */
    fun findByProjectIdAndUserIdAndStatus(
        projectId: Int,
        userId: Long,
        status: MemberStatus,
    ): ProjectMember?

    /** 指定项目的全部关系记录（按状态过滤，列成员用）。 */
    fun findByProjectIdAndStatus(projectId: Int, status: MemberStatus): List<ProjectMember>

    /** 指定项目的活跃 OWNER（用于「唯一拥有者」「转交前查」）。 */
    fun findByProjectIdAndRoleAndStatus(
        projectId: Int,
        role: ProjectRole,
        status: MemberStatus,
    ): List<ProjectMember>

    /** 用户当前活跃的全部项目成员关系（用户「我的项目」列表用）。 */
    fun findByUserIdAndStatus(userId: Long, status: MemberStatus): List<ProjectMember>

    /** 用户在某项目的任意状态关系记录（去重判断：是否已是成员）。 */
    fun findByProjectIdAndUserId(projectId: Int, userId: Long): ProjectMember?
}
