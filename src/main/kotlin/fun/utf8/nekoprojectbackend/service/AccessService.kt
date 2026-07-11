package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import org.springframework.stereotype.Service

/**
 * 接口鉴权辅助：按账号等级（[Role]）与项目归属判定访问权限。
 *
 * - 总管理（SUPER_ADMIN）：可访问全部项目与账号接口；
 * - 项目管理（PROJECT_MANAGER）：仅可访问 ownerId 等于自身 id 的项目。
 *
 * 本项目不在 Spring Security 层做角色拦截（authorities 为空），
 * 统一在控制器入口调用本服务做软判定，失败抛 403。
 */
@Service
class AccessService(
    private val objectItemRepository: ObjectItemRepository,
) {
    /** 仅总管理可通过；否则 403。用于邀请码生成、分配项目、创建总管理等敏感操作。 */
    fun requireSuperAdmin(user: LoginUser) {
        if (user.role != Role.SUPER_ADMIN) {
            throw ForbiddenException("需要总管理权限")
        }
    }

    /** 校验当前用户能否管理指定项目：总管理放行；项目管理仅限 ownerId 等于自身 id 的名下项目。 */
    fun ensureCanManage(user: LoginUser, objectItemId: Int) {
        if (user.role == Role.SUPER_ADMIN) return
        val ownerId = objectItemRepository.findById(objectItemId)
            .orElseThrow { ResourceNotFoundException("项目条目不存在") }
            .ownerId
        if (ownerId != user.id) {
            throw ForbiddenException("无权管理该项目")
        }
    }

    /** 列表场景的归属过滤：总管理返回 null（不限），项目管理返回自身 id。 */
    fun ownerIdScope(user: LoginUser): Long? =
        if (user.role == Role.SUPER_ADMIN) null else user.id
}
