package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceConflictException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import `fun`.utf8.nekoprojectbackend.service.JoinApplicationSaveRequest
import `fun`.utf8.nekoprojectbackend.service.JoinApplicationService
import `fun`.utf8.nekoprojectbackend.service.NeedMemberItemRequest
import `fun`.utf8.nekoprojectbackend.service.MindSaveRequest
import `fun`.utf8.nekoprojectbackend.service.MindService
import `fun`.utf8.nekoprojectbackend.service.MindQueryRequest
import `fun`.utf8.nekoprojectbackend.service.MindUpdateRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemQueryRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentSaveRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemCommentService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemManagementService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemPasswordChangeRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateManageCreateRequest
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateManagementService
import `fun`.utf8.nekoprojectbackend.service.ObjectItemUpdateRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHubWorkflowTests @Autowired constructor(
    private val objectItemService: ObjectItemService,
    private val objectItemManagementService: ObjectItemManagementService,
    private val mindService: MindService,
    private val joinApplicationService: JoinApplicationService,
    private val objectItemCommentService: ObjectItemCommentService,
    private val objectItemUpdateManagementService: ObjectItemUpdateManagementService,
) {

    @Test
    fun `legacy approved project becomes preparing and public count excludes review rows`() {
        val project = objectItemService.save(
            ObjectItemSaveRequest(
                title = "Public project",
                type = "BUILD",
                leader = "Owner",
                controlPassword = "secret",
            ),
        )

        val preparing = objectItemService.update(
            project.id!!,
            ObjectItemUpdateRequest(status = ObjectItemStatus.APPROVED, progress = 20),
        )

        assertEquals(ObjectItemStatus.PREPARING, preparing.status)
        assertEquals(20, preparing.progress)
        assertEquals(1, objectItemService.countPublic())

        assertThrows<ParamErrorException> {
            objectItemService.update(
                project.id,
                ObjectItemUpdateRequest(progress = 101),
            )
        }
    }

    @Test
    fun `tracked idea can be read with its token but not with another token`() {
        val tracked = mindService.saveTracked(
            MindSaveRequest(
                title = "Tracked idea",
                content = "A useful idea",
                nickName = "Visitor",
                mcId = "visitor",
            ),
        )

        val ideaId = tracked.value.id ?: error("tracked idea id was not assigned")
        assertTrue(tracked.trackingToken.isNotBlank())
        assertEquals(ideaId, mindService.findTracked(ideaId, tracked.trackingToken).id)
        assertThrows<ResourceNotFoundException> {
            mindService.findTracked(ideaId, "wrong-token")
        }
    }

    @Test
    fun `tracked join application can be read with its token`() {
        val project = objectItemService.save(
            ObjectItemSaveRequest(
                title = "Joinable project",
                type = "RPG",
                leader = "Owner",
                needMembers = listOf(NeedMemberItemRequest(skill = "Builder", number = 2)),
            ),
        )
        objectItemService.update(
            project.id!!,
            ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING),
        )

        val tracked = joinApplicationService.createTracked(
            project.id,
            JoinApplicationSaveRequest(
                nickName = "Visitor",
                mcId = "visitor",
                contact = "contact",
                reason = "I can help",
                skill = "Builder",
            ),
        )

        val applicationId = tracked.value.id ?: error("tracked application id was not assigned")
        assertTrue(tracked.trackingToken.isNotBlank())
        assertEquals(
            applicationId,
            joinApplicationService.findTracked(project.id, applicationId, tracked.trackingToken).id,
        )
        assertThrows<ResourceNotFoundException> {
            joinApplicationService.findTracked(project.id, applicationId, "wrong-token")
        }
    }

    @Test
    fun `very large page numbers return an empty page without integer overflow`() {
        val projectPage = objectItemService.queryPublicPage(
            request = `fun`.utf8.nekoprojectbackend.service.ObjectItemQueryRequest(),
            page = Int.MAX_VALUE,
            size = 500,
            sort = "id,desc",
        )
        val mindPage = mindService.queryPublicPage(
            request = `fun`.utf8.nekoprojectbackend.service.MindQueryRequest(),
            page = Int.MAX_VALUE,
            size = 500,
            sort = "createTime,desc",
        )

        assertTrue(projectPage.content.isEmpty())
        assertTrue(mindPage.content.isEmpty())
    }

    @Test
    fun `project control passwords stay within the bcrypt input policy`() {
        assertThrows<ParamErrorException> {
            objectItemService.save(projectRequest("Short password", "12345"))
        }
        assertThrows<ParamErrorException> {
            objectItemService.save(projectRequest("Long password", "a".repeat(73)))
        }

        val project = objectItemService.save(projectRequest("Change password", "secret"))
        assertThrows<ParamErrorException> {
            objectItemManagementService.changePassword(
                project.id!!,
                ObjectItemPasswordChangeRequest(
                    controlPassword = "secret",
                    newControlPassword = "中".repeat(25),
                ),
            )
        }
    }

    @Test
    fun `public submissions enforce bounded text and tag collections`() {
        assertThrows<ParamErrorException> {
            objectItemService.save(
                projectRequest("Oversized description").copy(description = "a".repeat(20_001)),
            )
        }
        assertThrows<ParamErrorException> {
            objectItemService.save(
                projectRequest("Too many tags").copy(tags = (1..13).map { "tag-$it" }),
            )
        }
        assertThrows<ParamErrorException> {
            mindService.save(MindSaveRequest(title = "Large idea", content = "a".repeat(10_001)))
        }

        val project = objectItemService.save(projectRequest("Bounded public input", "secret"))
        objectItemService.update(project.id!!, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))

        assertThrows<ParamErrorException> {
            joinApplicationService.create(
                project.id,
                JoinApplicationSaveRequest(
                    nickName = "Visitor",
                    mcId = "visitor",
                    contact = "contact",
                    reason = "a".repeat(4_001),
                    skill = "Builder",
                ),
            )
        }
        assertThrows<ParamErrorException> {
            objectItemCommentService.create(
                project.id,
                ObjectItemCommentSaveRequest(nickName = "Visitor", content = "a".repeat(2_001)),
            )
        }
        assertThrows<ParamErrorException> {
            objectItemUpdateManagementService.create(
                project.id,
                ObjectItemUpdateManageCreateRequest(
                    controlPassword = "secret",
                    title = "Large update",
                    content = "a".repeat(10_001),
                ),
            )
        }
    }

    @Test
    fun `unsafe image urls are rejected while same origin paths remain valid`() {
        listOf(
            "javascript:alert(1)",
            "data:image/svg+xml,<svg></svg>",
            "//untrusted.example.test/image.png",
        ).forEachIndexed { index, url ->
            assertThrows<ParamErrorException> {
                objectItemService.save(
                    projectRequest("Unsafe image $index").copy(coverImageUrl = url),
                )
            }
        }

        val project = objectItemService.save(
            projectRequest("Safe image path", "secret").copy(coverImageUrl = "/api/files/cover.png"),
        )
        assertEquals("/api/files/cover.png", project.coverImageUrl)

        objectItemService.update(project.id!!, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))
        assertThrows<ParamErrorException> {
            objectItemUpdateManagementService.create(
                project.id,
                ObjectItemUpdateManageCreateRequest(
                    controlPassword = "secret",
                    title = "Unsafe dynamic image",
                    content = "The image URL must not execute browser code.",
                    imageUrl = "vbscript:msgbox(1)",
                ),
            )
        }
    }

    @Test
    fun `query bounds and database paging remain enforced`() {
        val projectIds = (1..2).map { index ->
            objectItemService.save(
                projectRequest("Paged project $index").copy(tags = listOf("Redstone")),
            ).also { saved ->
                objectItemService.update(saved.id!!, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))
            }.id!!
        }

        val firstProjectPage = objectItemService.queryPublicPage(
            ObjectItemQueryRequest(tags = listOf("rEdStOnE")),
            page = 0,
            size = 1,
            sort = "id,asc",
        )
        val secondProjectPage = objectItemService.queryPublicPage(
            ObjectItemQueryRequest(tags = listOf("REDSTONE")),
            page = 1,
            size = 1,
            sort = "id,asc",
        )
        assertEquals(2, firstProjectPage.totalElements)
        assertEquals(listOf(projectIds[0]), firstProjectPage.content.mapNotNull { it.id })
        assertEquals(listOf(projectIds[1]), secondProjectPage.content.mapNotNull { it.id })

        (1..2).forEach { index ->
            val idea = mindService.save(
                MindSaveRequest(title = "Paged idea $index", content = "Searchable content $index"),
            )
            mindService.update(idea.id!!, MindUpdateRequest(status = `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus.APPROVED))
        }
        val ideaPage = mindService.queryPublicPage(
            MindQueryRequest(title = "PAGED IDEA"),
            page = 0,
            size = 1,
            sort = "createTime,desc",
        )
        assertEquals(2, ideaPage.totalElements)
        assertEquals(1, ideaPage.content.size)

        assertThrows<ParamErrorException> {
            objectItemService.queryPage(ObjectItemQueryRequest(ids = (1..101).toList()), 0, 20, "id,desc")
        }
        assertThrows<ParamErrorException> {
            mindService.queryPage(MindQueryRequest(title = "x".repeat(129)), 0, 20, "id,desc")
        }
        assertThrows<ParamErrorException> {
            objectItemService.queryPage(ObjectItemQueryRequest(), 0, 20, "id,asc,extra")
        }
        assertThrows<ParamErrorException> {
            mindService.queryPage(MindQueryRequest(), 0, 20, "id,asc,extra")
        }
        assertThrows<ParamErrorException> {
            objectItemService.queryPage(ObjectItemQueryRequest(), 0, 501, "id,desc")
        }
    }

    @Test
    fun `join applications require recruiting status and a declared role`() {
        val project = objectItemService.save(projectRequest("Recruitment rules"))
        objectItemService.update(project.id!!, ObjectItemUpdateRequest(status = ObjectItemStatus.PREPARING))
        val request = JoinApplicationSaveRequest(
            nickName = "Visitor",
            mcId = "visitor",
            contact = "contact",
            reason = "I can help",
            skill = "Builder",
        )

        assertThrows<ResourceConflictException> {
            joinApplicationService.create(project.id, request)
        }

        objectItemService.update(project.id, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))
        assertThrows<ParamErrorException> {
            joinApplicationService.create(project.id, request.copy(skill = "Miner"))
        }
    }

    @Test
    fun `soft deleted projects do not consume the manager project quota`() {
        val ownerId = 9_999L
        repeat(10) { index ->
            val project = objectItemService.saveOwned(
                projectRequest("Deleted project $index"),
                ownerId,
                ObjectItemStatus.PENDING,
            )
            objectItemService.update(project.id!!, ObjectItemUpdateRequest(status = ObjectItemStatus.DELETED))
        }

        val activeProject = objectItemService.saveOwned(
            projectRequest("Replacement project"),
            ownerId,
            ObjectItemStatus.PENDING,
        )
        assertEquals(ownerId, activeProject.ownerId)
    }

    private fun projectRequest(title: String, controlPassword: String? = null) = ObjectItemSaveRequest(
        title = title,
        type = "BUILD",
        leader = "Owner",
        needMembers = listOf(NeedMemberItemRequest(skill = "Builder", number = 2)),
        controlPassword = controlPassword,
    )
}
