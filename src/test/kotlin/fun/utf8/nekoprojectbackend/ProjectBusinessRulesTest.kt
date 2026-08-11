package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.config.ModerationProperties
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.AuditLogRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplication
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.JoinApplicationStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.NeedMemberItem
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemComment
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemCommentStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.TagRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.UserRepository
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.service.JoinApplicationSaveRequest
import `fun`.utf8.nekoprojectbackend.service.JoinApplicationService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemService
import `fun`.utf8.nekoprojectbackend.service.OperationLogService
import `fun`.utf8.nekoprojectbackend.service.TagService
import java.nio.file.Files
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito

class ProjectBusinessRulesTest {

    @Test
    fun `join applications require a public recruiting project`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val joinApplicationRepository = Mockito.mock(JoinApplicationRepository::class.java)
        val service = JoinApplicationService(objectItemRepository, joinApplicationRepository)
        Mockito.`when`(objectItemRepository.findById(1)).thenReturn(
            Optional.of(project(status = ObjectItemStatus.PREPARING)),
        )

        assertThrows<ParamErrorException> {
            service.create(1, joinRequest(skill = "建筑"))
        }

        Mockito.verify(joinApplicationRepository, Mockito.never()).save(Mockito.any(JoinApplication::class.java))
    }

    @Test
    fun `join applications reject hidden projects as not found`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val joinApplicationRepository = Mockito.mock(JoinApplicationRepository::class.java)
        val service = JoinApplicationService(objectItemRepository, joinApplicationRepository)
        Mockito.`when`(objectItemRepository.findById(1)).thenReturn(
            Optional.of(project(status = ObjectItemStatus.PENDING)),
        )

        assertThrows<ResourceNotFoundException> {
            service.create(1, joinRequest(skill = "建筑"))
        }

        Mockito.verify(joinApplicationRepository, Mockito.never()).save(Mockito.any(JoinApplication::class.java))
    }

    @Test
    fun `join applications require an open role with remaining slots`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val joinApplicationRepository = Mockito.mock(JoinApplicationRepository::class.java)
        val service = JoinApplicationService(objectItemRepository, joinApplicationRepository)
        Mockito.`when`(objectItemRepository.findById(1)).thenReturn(
            Optional.of(project(status = ObjectItemStatus.RECRUITING, skill = "建筑", slots = 1)),
        )
        Mockito.`when`(
            joinApplicationRepository.findByObjectItemIdAndStatus(1, JoinApplicationStatus.ACCEPTED),
        ).thenReturn(listOf(JoinApplication().apply { skill = "建筑" }))

        assertThrows<ParamErrorException> {
            service.create(1, joinRequest(skill = "建筑"))
        }

        Mockito.verify(joinApplicationRepository, Mockito.never()).save(Mockito.any(JoinApplication::class.java))
    }

    @Test
    fun `hidden projects reject public comments`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val commentRepository = Mockito.mock(ObjectItemCommentRepository::class.java)
        val service = ObjectItemCommentService(
            objectItemRepository,
            commentRepository,
            ModerationProperties(enabled = false),
        )
        Mockito.`when`(objectItemRepository.findById(2)).thenReturn(
            Optional.of(project(id = 2, status = ObjectItemStatus.REJECTED)),
        )

        assertThrows<ResourceNotFoundException> {
            service.create(2, ObjectItemCommentSaveRequest(nickName = "Alice", content = "hi"))
        }

        Mockito.verify(commentRepository, Mockito.never()).save(Mockito.any(ObjectItemComment::class.java))
    }

    @Test
    fun `soft deleted projects do not consume the owner project quota`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val service = objectItemService(objectItemRepository, maxPerManager = 1)
        Mockito.`when`(
            objectItemRepository.countByOwnerIdAndStatusNot(7, ObjectItemStatus.DELETED),
        ).thenReturn(0)
        Mockito.`when`(objectItemRepository.save(Mockito.any(ObjectItem::class.java))).thenAnswer {
            it.getArgument<ObjectItem>(0).apply { id = 44 }
        }

        val saved = service.saveOwned(ObjectItemSaveRequest(title = "Fresh project"), 7, ObjectItemStatus.PENDING)

        assertEquals(44, saved.id)
        Mockito.verify(objectItemRepository).countByOwnerIdAndStatusNot(7, ObjectItemStatus.DELETED)
    }

    @Test
    fun `unsafe cover image urls are rejected before persistence`() {
        val objectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
        val service = objectItemService(objectItemRepository)
        Mockito.`when`(
            objectItemRepository.countByOwnerIdAndStatusNot(7, ObjectItemStatus.DELETED),
        ).thenReturn(0)

        assertThrows<ParamErrorException> {
            service.saveOwned(
                ObjectItemSaveRequest(title = "Unsafe image", coverImageUrl = "javascript:alert(1)"),
                7,
                ObjectItemStatus.PENDING,
            )
        }

        Mockito.verify(objectItemRepository, Mockito.never()).save(Mockito.any(ObjectItem::class.java))
    }

    @Test
    fun `operation log escapes all json control characters`() {
        val path = Files.createTempDirectory("neko-operation-log-test").resolve("operation.log")
        val service = OperationLogService(
            auditLogRepository = Mockito.mock(AuditLogRepository::class.java),
            logPath = path.toString(),
            maxSizeMb = 1,
            maxArchives = 3,
            enabled = true,
            trustForwarded = false,
        )
        service.init()

        service.record(
            action = "TEST",
            targetType = "PROJECT",
            description = "line\u0001break\bend",
        )
        service.shutdown()

        val line = Files.readString(path)
        assertTrue(line.contains("\\u0001"))
        assertTrue(line.contains("\\b"))
        assertFalse(line.contains('\u0001'))
    }

    private fun objectItemService(
        objectItemRepository: ObjectItemRepository,
        maxPerManager: Long = 10,
    ): ObjectItemService {
        val userRepository = Mockito.mock(UserRepository::class.java)
        val tagRepository = Mockito.mock(TagRepository::class.java)
        val tagService = TagService(tagRepository, objectItemRepository)
        return ObjectItemService(objectItemRepository, userRepository, tagService, maxPerManager)
    }

    private fun joinRequest(skill: String?) = JoinApplicationSaveRequest(
        nickName = "Alice",
        mcId = "AliceMC",
        contact = "alice@example.test",
        reason = "I want to join",
        skill = skill,
    )

    private fun project(
        id: Int = 1,
        status: ObjectItemStatus,
        skill: String = "建筑",
        slots: Long = 2,
    ) = ObjectItem().apply {
        this.id = id
        this.title = "Project $id"
        this.status = status
        this.needMembers = mutableListOf(
            NeedMemberItem().apply {
                this.skill = skill
                this.number = slots
                this.context = "build"
            },
        )
    }
}
