package `fun`.utf8.nekoprojectbackend.controller

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Role
import `fun`.utf8.nekoprojectbackend.handlder.ForbiddenException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.AccessService
import `fun`.utf8.nekoprojectbackend.service.JoinApplicationService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemBatchSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemResponse
import `fun`.utf8.nekoprojectbackend.service.ObjectItemSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.shared.ResponseBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito

/**
 * 公开端项目创建（POST /api/project/object-items[/batch]）鉴权回归：
 * 必须先经 [AccessService.ensureCanCreateProject] 资格校验，再以当前用户为归属、强制 PENDING 待审，
 * 客户端传入的 status 一律忽略（防绕过审核门禁，设计 §2.2 / §14）。
 *
 * 纯 Mockito 单测，不启 Spring 上下文，不触库。
 *
 * 注：不用 ArgumentMatchers.eq/any——Kotlin 非空参数在 mock 方法入口插入 Intrinsics 空检查，
 * `eq()/any()` 返回 null 即抛 NPE。改用原始值匹配（Mockito 按相等性判定）+ verifyNoInteractions。
 */
class ObjectItemControllerTest {

    private val objectItemService = Mockito.mock(ObjectItemService::class.java)
    private val accessService = Mockito.mock(AccessService::class.java)

    private val controller = ObjectItemController(
        objectItemService = objectItemService,
        objectItemUpdateService = Mockito.mock(ObjectItemUpdateService::class.java),
        objectItemCommentService = Mockito.mock(ObjectItemCommentService::class.java),
        joinApplicationService = Mockito.mock(JoinApplicationService::class.java),
        accessService = accessService,
        operationLogService = Mockito.mock(OperationLogService::class.java),
        builder = ResponseBuilder(),
    )

    private fun user(id: Long, name: String) = LoginUser(id, name, Role.USER, "jti-$id")

    private fun response(id: Int = 1) = ObjectItemResponse(
        id = id,
        title = "t",
        introduction = null,
        description = null,
        status = ObjectItemStatus.PENDING,
        leader = null,
        needMembers = emptyList(),
        tags = emptyList(),
        leaderMcId = null,
        contactInformation = null,
        coverImageUrl = null,
        ownerId = null,
    )

    @Test
    fun `save gates on ensureCanCreateProject and forces PENDING with caller as owner`() {
        val user = user(7, "alice")
        // 故意传 APPROVED：验证控制器改以 PENDING 调用 saveOwned，不透传客户端状态
        val request = ObjectItemSaveRequest(title = "T").apply { status = ObjectItemStatus.APPROVED }

        Mockito.`when`(objectItemService.saveOwned(request, 7L, ObjectItemStatus.PENDING))
            .thenReturn(response())

        controller.save(user, request)

        // 按相等性精确断言：归属=当前用户、状态强制 PENDING（即便请求体传 APPROVED）
        Mockito.verify(objectItemService).saveOwned(request, 7L, ObjectItemStatus.PENDING)
        Mockito.verify(accessService).ensureCanCreateProject(user)
    }

    @Test
    fun `save rejects caller lacking create-project eligibility`() {
        val user = user(8, "bob")
        val request = ObjectItemSaveRequest(title = "T")

        Mockito.doThrow(ForbiddenException("无项目创建资格"))
            .`when`(accessService).ensureCanCreateProject(user)

        assertThrows<ForbiddenException> { controller.save(user, request) }

        // 资格不通过：不得触达任何创建路径
        Mockito.verifyNoInteractions(objectItemService)
    }

    @Test
    fun `saveBatch gates on ensureCanCreateProject and forces PENDING across the whole batch`() {
        val user = user(9, "carol")
        val items = listOf(
            ObjectItemSaveRequest(title = "A").apply { status = ObjectItemStatus.RECRUITING },
            ObjectItemSaveRequest(title = "B"),
        )
        val request = ObjectItemBatchSaveRequest(items = items)

        Mockito.`when`(objectItemService.saveBatchOwned(items, 9L, ObjectItemStatus.PENDING))
            .thenReturn(listOf(response(1), response(2)))

        controller.saveBatch(user, request)

        // 整批归属当前用户、状态统一强制 PENDING（即便首条请求体传 RECRUITING）
        Mockito.verify(objectItemService).saveBatchOwned(items, 9L, ObjectItemStatus.PENDING)
        Mockito.verify(accessService).ensureCanCreateProject(user)
    }

    @Test
    fun `saveBatch rejects caller lacking create-project eligibility`() {
        val user = user(10, "dave")
        val request = ObjectItemBatchSaveRequest(items = listOf(ObjectItemSaveRequest(title = "A")))

        Mockito.doThrow(ForbiddenException("无项目创建资格"))
            .`when`(accessService).ensureCanCreateProject(user)

        assertThrows<ForbiddenException> { controller.saveBatch(user, request) }

        Mockito.verifyNoInteractions(objectItemService)
    }
}
