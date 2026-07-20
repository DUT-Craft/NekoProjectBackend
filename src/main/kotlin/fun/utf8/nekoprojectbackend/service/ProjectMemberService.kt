package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MemberStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectMember
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectMemberRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectRole
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.handlder.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class AddMemberRequest(
    val userId: Long,
    val role: ProjectRole = ProjectRole.MEMBER,
)

data class TransferOwnerRequest(
    val newOwnerId: Long,
)

data class ProjectMemberResponse(
    val id: Long?,
    val projectId: Int?,
    val userId: Long?,
    val role: ProjectRole?,
    val status: MemberStatus?,
    val joinedAt: LocalDateTime?,
    val leftAt: LocalDateTime?,
)

/**
 * 项目成员管理业务（设计 §9）：加入 / 退出 / 移除 / 转交。
 *
 * - 退出 / 移除走软状态（[MemberStatus.LEFT] / [MemberStatus.REMOVED]），保留审计链路，不物理删行；
 * - OWNER 不能直接退出 / 被移除，必须先转交（设计 §9.2）；
 * - 转交：旧 OWNER 降为 MEMBER，新成员升为 OWNER，同步 [fun.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem.ownerId]。
 */
@Service
class ProjectMemberService(
    private val projectMemberRepository: ProjectMemberRepository,
    private val objectItemRepository: ObjectItemRepository,
    private val userService: UserService,
) {

    @Transactional(readOnly = true)
    fun listActive(projectId: Int): List<ProjectMemberResponse> {
        return projectMemberRepository
            .findByProjectIdAndStatus(projectId, MemberStatus.ACTIVE)
            .sortedWith(compareBy { roleRank(it.role) })
            .map { it.toResponse() }
    }

    /** OWNER 添加 / 重新激活成员（幂等 upsert：已有关系则更新角色与状态）。 */
    @Transactional
    fun addMember(projectId: Int, req: AddMemberRequest, invitedBy: Long): ProjectMemberResponse {
        ensureProjectExists(projectId)
        if (req.role == ProjectRole.OWNER) {
            throw ParamErrorException("请使用转交接口变更拥有者")
        }
        userService.findById(req.userId) ?: throw UserNotFoundException()
        val existing = projectMemberRepository.findByProjectIdAndUserId(projectId, req.userId)
        val member = (existing ?: ProjectMember().apply {
            this.projectId = projectId
            this.userId = req.userId
            this.invitedBy = invitedBy
        }).apply {
            role = req.role
            status = MemberStatus.ACTIVE
            if (joinedAt == null) joinedAt = LocalDateTime.now()
            leftAt = null
        }
        return projectMemberRepository.save(member).toResponse()
    }

    /** OWNER 移除成员或撤管理员（软状态 REMOVED）。不能移除 OWNER。 */
    @Transactional
    fun removeMember(projectId: Int, userId: Long): ProjectMemberResponse {
        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResourceNotFoundException("成员不存在")
        if (member.role == ProjectRole.OWNER) {
            throw ParamErrorException("不能移除项目拥有者，请先转交所有权")
        }
        member.status = MemberStatus.REMOVED
        member.leftAt = LocalDateTime.now()
        return projectMemberRepository.save(member).toResponse()
    }

    /** OWNER 转交所有权（设计 §9.2）：旧 OWNER→MEMBER，新成员→OWNER，同步 ObjectItem.ownerId。 */
    @Transactional
    fun transferOwnership(projectId: Int, newOwnerId: Long): ProjectMemberResponse {
        ensureProjectExists(projectId)
        if (userService.findById(newOwnerId) == null) throw UserNotFoundException()

        // 旧 OWNER（们）降为 MEMBER，保留为成员
        val currentOwners = projectMemberRepository
            .findByProjectIdAndRoleAndStatus(projectId, ProjectRole.OWNER, MemberStatus.ACTIVE)
        currentOwners.forEach { it.role = ProjectRole.MEMBER }
        projectMemberRepository.saveAll(currentOwners)

        // 新拥有者 upsert → OWNER
        val existing = projectMemberRepository.findByProjectIdAndUserId(projectId, newOwnerId)
        val newOwner = (existing ?: ProjectMember().apply {
            this.projectId = projectId
            this.userId = newOwnerId
            joinedAt = LocalDateTime.now()
        }).apply {
            role = ProjectRole.OWNER
            status = MemberStatus.ACTIVE
            leftAt = null
        }
        projectMemberRepository.save(newOwner)

        // 同步去规范化指针 ObjectItem.ownerId（过渡期，阶段八下线）
        objectItemRepository.findById(projectId)
            .orElseThrow { ResourceNotFoundException("项目不存在") }
            .apply { ownerId = newOwnerId }
            .also { objectItemRepository.save(it) }

        return newOwner.toResponse()
    }

    /** MEMBER / MANAGER 主动退出（软状态 LEFT）。OWNER 禁止直接退出。 */
    @Transactional
    fun leave(projectId: Int, userId: Long): ProjectMemberResponse {
        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResourceNotFoundException("你不是该项目成员")
        if (member.role == ProjectRole.OWNER) {
            throw ParamErrorException("项目拥有者不能直接退出，请先转交所有权")
        }
        member.status = MemberStatus.LEFT
        member.leftAt = LocalDateTime.now()
        return projectMemberRepository.save(member).toResponse()
    }

    /**
     * 接受加入申请时调用：幂等 upsert 为 ACTIVE MEMBER（设计 §9.1）。
     * 已是成员则不降级（避免把 OWNER/MANAGER 误降为 MEMBER）。
     */
    @Transactional
    fun upsertMemberOnAccept(projectId: Int, userId: Long): ProjectMember {
        val existing = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
        if (existing != null) {
            // 已有关系：若已 LEFT/REMOVED 则重新激活为 MEMBER；ACTIVE 的 OWNER/MANAGER 不降级
            if (existing.status != MemberStatus.ACTIVE) {
                existing.status = MemberStatus.ACTIVE
                existing.role = ProjectRole.MEMBER
                existing.leftAt = null
                if (existing.joinedAt == null) existing.joinedAt = LocalDateTime.now()
                return projectMemberRepository.save(existing)
            }
            return existing
        }
        return projectMemberRepository.save(
            ProjectMember().apply {
                this.projectId = projectId
                this.userId = userId
                this.role = ProjectRole.MEMBER
                this.status = MemberStatus.ACTIVE
                this.joinedAt = LocalDateTime.now()
            }
        )
    }

    private fun ensureProjectExists(projectId: Int) {
        if (!objectItemRepository.existsById(projectId)) {
            throw ResourceNotFoundException("项目不存在")
        }
    }

    private fun roleRank(role: ProjectRole?): Int = when (role) {
        ProjectRole.OWNER -> 0
        ProjectRole.MANAGER -> 1
        ProjectRole.MEMBER -> 2
        null -> 3
    }

    private fun ProjectMember.toResponse() = ProjectMemberResponse(
        id = id,
        projectId = projectId,
        userId = userId,
        role = role,
        status = status,
        joinedAt = joinedAt,
        leftAt = leftAt,
    )
}
