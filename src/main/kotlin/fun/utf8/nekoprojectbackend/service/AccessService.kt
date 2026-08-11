package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MemberStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectMemberRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ProjectRole
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Status
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import org.springframework.stereotype.Service

/**
 * 接口鉴权辅助：按系统角色（[Role]）与项目内角色（[ProjectRole]）判定访问权限。
 *
 * - [Role.SUPER_ADMIN]：可访问全部项目与账号接口（但受最后管理员保护、审计等约束）；
 * - 项目 OWNER / MANAGER：可管理该项目（成员关系来自 [ProjectMemberRepository]）；
 * - 迁移期：旧 [fun.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem.ownerId] 仍生效（阶段八下线）。
 *
 * 本项目不在 Spring Security 层做角色拦截（authorities 为空），
 * 统一在控制器入口调用本服务做软判定，失败抛 403。
 *
 * 角色时效（设计 §14 修正）：JWT role claim 仅反映签发时的角色，不重读 DB。
 * 封禁 / 停用 / 注销靠 [TokenStore.invalidateAllSessions] 立即清白名单，但「降级不封禁」场景
 * 旧 access token 在 TTL 内仍带旧角色。故权限敏感判定（[requireSuperAdmin] / [ownerIdScope]）
 * 一律重读 [UserService] 取真实角色与状态，避免凭过期 token 越权。
 */
@Service
class AccessService(
    private val objectItemRepository: ObjectItemRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userService: UserService,
) {
    /** 仅总管理可通过；否则 403。用于邀请码生成、分配项目、创建总管理等敏感操作。 */
    fun requireSuperAdmin(user: LoginUser) {
        // 重读 DB：防「降级不封禁」场景下旧 access token 仍带 SUPER_ADMIN 越权（设计 §14 修正）。
        // 仅 ACTIVE 且真实角色为 SUPER_ADMIN 放行；被封禁/停用/注销的账号其会话应已被清白名单，
        // 此处再判一次 status 作为深度防御。
        val fullUser = userService.findById(user.id)
        if (fullUser?.role != Role.SUPER_ADMIN || fullUser.status != Status.ACTIVE) {
            throw ForbiddenException("需要总管理权限")
        }
    }

    /** 创建项目资格校验（设计 §2.2）：超级管理员或 canCreateProject=true 的用户，否则 403。 */
    fun ensureCanCreateProject(user: LoginUser) {
        if (user.role == Role.SUPER_ADMIN) return
        val fullUser = userService.findById(user.id)
        if (fullUser?.canCreateProject != true) {
            throw ForbiddenException("无项目创建资格，请先获取邀请码或联系总管理")
        }
    }

    /**
     * 校验当前用户能否管理指定项目：总管理放行；项目 OWNER/MANAGER 放行；过渡期旧 ownerId 命中放行。
     * 其余（MEMBER / 非成员）403。
     */
    fun ensureCanManage(user: LoginUser, objectItemId: Int) {
        if (isCurrentUserSuperAdmin(user)) return
        if (isProjectManager(user.id, objectItemId)) return
        throw ForbiddenException("无权管理该项目")
    }

    /** 仅项目 OWNER（或总管理）可通过：用于转交 / 删除 / 授权管理员（设计 §2.3）。 */
    fun ensureIsOwner(user: LoginUser, objectItemId: Int) {
        if (isCurrentUserSuperAdmin(user)) return
        val member = projectMemberRepository
            .findByProjectIdAndUserIdAndStatus(objectItemId, user.id, MemberStatus.ACTIVE)
        if (member?.role == ProjectRole.OWNER) return
        // 过渡兜底：旧 ownerId 命中视为 OWNER
        if (isLegacyOwner(user.id, objectItemId)) return
        throw ForbiddenException("需要项目拥有者权限")
    }

    /**
     * 校验能否管理某条用户资源（评论 / 想法 / 动态等，设计 §8.1）：
     * 作者本人、所属项目的 OWNER/MANAGER、超管均可；否则 403。
     *
     * @param authorUserId 资源作者用户 ID（历史匿名为 null——匿名资源仅项目管理者/超管可管）
     * @param objectItemId 资源所属项目（想法类无项目时传 null，退化为作者本人/超管）
     */
    fun ensureCanManageResource(user: LoginUser, authorUserId: Long?, objectItemId: Int?) {
        if (isCurrentUserSuperAdmin(user)) return
        if (authorUserId != null && authorUserId == user.id) return
        if (objectItemId != null && isProjectManager(user.id, objectItemId)) return
        throw ForbiddenException("无权管理该资源")
    }

    /** 当前用户是否为指定项目的活跃 OWNER/MANAGER（不含过渡旧 ownerId）。 */
    fun isProjectManager(userId: Long, objectItemId: Int): Boolean {
        val member = projectMemberRepository
            .findByProjectIdAndUserIdAndStatus(objectItemId, userId, MemberStatus.ACTIVE)
            if (member != null && (member.role == ProjectRole.OWNER || member.role == ProjectRole.MANAGER)) {
            return true
        }
        return isLegacyOwner(userId, objectItemId)
    }

    /** 过渡兜底：旧 ownerId 命中视为管理者（阶段八下线 [ObjectItem.ownerId] 后删除）。 */
    private fun isLegacyOwner(userId: Long, objectItemId: Int): Boolean {
        val ownerId = objectItemRepository.findById(objectItemId)
            .orElseThrow { ResourceNotFoundException("项目条目不存在") }
            .ownerId
        return ownerId == userId
    }

    /** 列表场景的归属过滤：总管理返回 null（不限）；否则返回自身 id（过渡期语义不变）。 */
    fun ownerIdScope(user: LoginUser): Long? =
        if (isCurrentUserSuperAdmin(user)) null else user.id

    /**
     * 重读 DB 校验当前用户是否仍为活跃超级管理员。
     * 防「降级不封禁」场景下 access token 携带旧 SUPER_ADMIN 角色越权（设计 §14 修正）。
     * 被封禁/停用/注销的账号即便 token 仍有效也在此拦下（深度防御，白名单本应已清）。
     */
    private fun isCurrentUserSuperAdmin(user: LoginUser): Boolean {
        val fullUser = userService.findById(user.id) ?: return false
        return fullUser.role == Role.SUPER_ADMIN && fullUser.status == Status.ACTIVE
    }
}
