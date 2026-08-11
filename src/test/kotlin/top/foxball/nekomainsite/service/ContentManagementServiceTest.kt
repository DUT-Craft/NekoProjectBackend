package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import top.foxball.nekomainsite.entity.jdbc.MediaAsset
import top.foxball.nekomainsite.entity.jdbc.ActivityRegistration
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.ActivityRegistrationRepository
import top.foxball.nekomainsite.repository.MediaAssetRepository
import top.foxball.nekomainsite.repository.ServerRepository
import java.util.UUID

@SpringBootTest
@Transactional
class ContentManagementServiceTest @Autowired constructor(
    private val service: ContentManagementService,
    private val announcements: AnnouncementRepository,
    private val registrations: ActivityRegistrationRepository,
    private val media: MediaAssetRepository,
    private val servers: ServerRepository,
    private val objectMapper: ObjectMapper,
) {
    @Test
    fun `incomplete new draft can be saved listed resumed and deleted`() {
        val created = service.createDraft("announcements", null, 1L)
        val partial = objectMapper.readTree(
            """{"slug":"draft-note","title":"尚未写完","summary":"","blocks":[],"priority":0,"pinned":false,"status":"DRAFT"}""",
        )

        val saved = service.saveDraft(created.id, created.version, partial, 1L)
        val listed = service.list("announcements").single { it.draftId == created.id }

        assertEquals("尚未写完", listed.title)
        assertEquals("DRAFT", listed.status)
        assertNull(listed.id)
        assertEquals(saved.version, service.draft(created.id).version)

        service.deleteDraft(created.id, 1L)
        assertFalse(service.list("announcements").any { it.draftId == created.id })
    }

    @Test
    fun `publishing validates fields and keeps edits private until publish`() {
        val created = service.createDraft("announcements", null, 1L)
        val incomplete = objectMapper.readTree(
            """{"slug":"flow-test","title":"流程公告","summary":"","blocks":[],"priority":1,"pinned":false,"status":"DRAFT"}""",
        )
        val incompleteDraft = service.saveDraft(created.id, created.version, incomplete, 1L)
        assertThrows(ParamErrorException::class.java) { service.publish(created.id, incompleteDraft.version, 1L) }

        val first = announcementPayload("第一版摘要", "第一版正文")
        val firstDraft = service.saveDraft(created.id, incompleteDraft.version, first, 1L)
        val firstPublished = service.publish(created.id, firstDraft.version, 1L)
        assertEquals("第一版摘要", announcements.findBySlug("flow-test")?.summary)

        val second = announcementPayload("第二版摘要", "第二版正文")
        val secondDraft = service.saveDraft(created.id, firstPublished.version, second, 1L)
        assertEquals("第一版摘要", announcements.findBySlug("flow-test")?.summary)

        val secondPublished = service.publish(created.id, secondDraft.version, 1L)
        assertEquals("第二版摘要", announcements.findBySlug("flow-test")?.summary)
        assertTrue(secondPublished.hasPreviousVersion)

        val resourceId = requireNotNull(secondPublished.resourceId)
        val restored = service.restorePrevious("announcements", resourceId, 1L)
        service.publish(restored.id, restored.version, 1L)
        assertEquals("第一版摘要", announcements.findBySlug("flow-test")?.summary)

        service.unpublish("announcements", secondPublished.resourceId, 1L)
        assertFalse(service.list("announcements").single { it.id == secondPublished.resourceId }.published)
    }

    @Test
    fun `publishing replacement media retires the old asset`() {
        val announcement = announcements.findBySlug("speedrun") ?: error("seed announcement missing")
        val announcementId = requireNotNull(announcement.id)
        val oldMedia = media.save(mediaAsset(resourceId = announcement.id))
        announcement.coverMediaId = oldMedia.id
        announcements.save(announcement)

        val draft = service.createDraft("announcements", announcement.id, 1L)
        val newMedia = media.save(mediaAsset(draftId = draft.id))
        val payload = objectMapper.readTree(
            """{"slug":"speedrun","title":"本周速通挑战今晚开局","category":"event","categoryLabel":"活动通知","summary":"更新封面","coverMediaId":${newMedia.id},"blocks":[{"type":"paragraph","text":"正文"}],"priority":100,"pinned":true,"status":"PUBLISHED"}""",
        )
        val saved = service.saveDraft(draft.id, draft.version, payload, 1L)
        service.publish(draft.id, saved.version, 1L)

        val oldMediaId = requireNotNull(oldMedia.id)
        val newMediaId = requireNotNull(newMedia.id)
        val retired = media.findById(oldMediaId).orElseThrow()
        val active = media.findById(newMediaId).orElseThrow()
        assertNotNull(retired.deletedAt)
        assertTrue(service.isMediaReferenced(oldMediaId), "上一版本引用的图片必须保留，不能被七天清理任务删除")
        assertNull(active.deletedAt)
        assertNull(active.draftId)
        assertEquals(announcement.id, active.resourceId)
        assertEquals(newMedia.id, announcements.findBySlug("speedrun")?.coverMediaId)

        val restored = service.restorePrevious("announcements", announcementId, 1L)
        assertNull(media.findById(oldMediaId).orElseThrow().deletedAt)
        service.publish(restored.id, restored.version, 1L)
        assertEquals(oldMedia.id, announcements.findBySlug("speedrun")?.coverMediaId)
    }

    @Test
    fun `draft cannot reference media owned by another content`() {
        val ownerDraft = service.createDraft("announcements", null, 1L)
        val foreignMedia = media.save(mediaAsset(draftId = ownerDraft.id))
        val otherDraft = service.createDraft("announcements", null, 1L)
        val payload = objectMapper.readTree(
            """{"slug":"other-content","title":"另一条公告","category":"test","categoryLabel":"测试","summary":"不允许跨内容引用图片","coverMediaId":${foreignMedia.id},"blocks":[{"type":"paragraph","text":"正文"}],"priority":0,"pinned":false,"status":"DRAFT"}""",
        )
        val saved = service.saveDraft(otherDraft.id, otherDraft.version, payload, 1L)

        assertThrows(ParamErrorException::class.java) { service.publish(otherDraft.id, saved.version, 1L) }
        assertNull(announcements.findBySlug("other-content"))
    }

    @Test
    fun `published image blocks must reference media owned by the draft`() {
        val invalidBlocks = listOf(
            """{"type":"image","src":"https://example.com/unowned.png","alt":"外部图片"}""",
            """{"type":"gallery","items":[{"src":"https://example.com/unowned.png","alt":"外部图片"}]}""",
            """{"type":"gallery","items":[]}""",
        )

        invalidBlocks.forEachIndexed { index, block ->
            val draft = service.createDraft("announcements", null, 1L)
            val payload = objectMapper.readTree(
                """{"slug":"invalid-media-$index","title":"非法图片引用","category":"test","categoryLabel":"测试","summary":"图片必须属于当前草稿","blocks":[$block],"priority":0,"pinned":false}""",
            )
            val saved = service.saveDraft(draft.id, draft.version, payload, 1L)

            assertThrows(ParamErrorException::class.java) { service.publish(draft.id, saved.version, 1L) }
            assertNull(announcements.findBySlug("invalid-media-$index"))
        }
    }

    @Test
    fun `published content must be taken offline before permanent deletion`() {
        val draft = service.createDraft("announcements", null, 1L)
        val saved = service.saveDraft(draft.id, draft.version, announcementPayload("待删除摘要", "待删除正文"), 1L)
        val published = service.publish(draft.id, saved.version, 1L)
        val resourceId = requireNotNull(published.resourceId)

        assertThrows(ConflictException::class.java) { service.deleteResource("announcements", resourceId, 1L) }
        service.unpublish("announcements", resourceId, 1L)
        service.deleteResource("announcements", resourceId, 1L)

        assertNull(announcements.findBySlug("flow-test"))
        assertFalse(service.list("announcements").any { it.id == published.resourceId })
    }

    @Test
    fun `server referenced by an activity cannot be permanently deleted`() {
        val server = service.list("servers").first { it.slug == "event" }
        val serverId = requireNotNull(server.id)
        service.unpublish("servers", serverId, 1L)

        assertThrows(ConflictException::class.java) { service.deleteResource("servers", serverId, 1L) }
        assertNotNull(servers.findById(serverId).orElse(null))
    }

    @Test
    fun `activity with registrations cannot be permanently deleted`() {
        val draft = service.createDraft("activities", null, 1L)
        val saved = service.saveDraft(draft.id, draft.version, activityPayload(), 1L)
        val published = service.publish(draft.id, saved.version, 1L)
        registrations.save(ActivityRegistration(
            activitySlug = "test-activity",
            minecraftId = "CleanupGuard",
            qq = "12345678",
        ))

        val resourceId = requireNotNull(published.resourceId)
        service.unpublish("activities", resourceId, 1L)
        assertThrows(ConflictException::class.java) {
            service.deleteResource("activities", resourceId, 1L)
        }
    }

    @Test
    fun `server runtime refresh fields do not create unpublished draft changes`() {
        val server = service.list("servers").first { it.slug == "redstone" }
        service.createDraft("servers", server.id, 1L)
        assertFalse(service.list("servers").single { it.id == server.id }.hasUnpublishedChanges)
        val entity = servers.findById(requireNotNull(server.id)).orElseThrow()
        entity.onlineCount += 1
        entity.capacity += 1
        entity.lastCheckedAt = java.time.Instant.now()
        servers.save(entity)
        assertFalse(service.list("servers").single { it.id == server.id }.hasUnpublishedChanges)
    }

    @Test
    fun `word style inline formatting is stored and published safely`() {
        val draft = service.createDraft("announcements", null, 1L)
        val payload = objectMapper.readTree(
            """{"slug":"word-editor","title":"Word 编辑器测试","category":"test","categoryLabel":"测试","summary":"验证结构化富文本","blocks":[{"type":"heading","level":2,"text":"使用说明","content":[{"type":"text","text":"使用","marks":[{"type":"bold"}]},{"type":"text","text":"说明"}],"align":"center"},{"type":"paragraph","text":"查看规则","content":[{"type":"text","text":"查看规则","marks":[{"type":"link","attrs":{"href":"/wiki/rules"}}]}]}],"priority":0,"pinned":false}"""
        )

        val saved = service.saveDraft(draft.id, draft.version, payload, 1L)
        val published = service.publish(draft.id, saved.version, 1L)

        assertNotNull(published.resourceId)
        val announcement = requireNotNull(announcements.findBySlug("word-editor"))
        assertTrue(announcement.contentBlocks.contains("underline").not())
        assertTrue(announcement.contentBlocks.contains("bold"))
        assertEquals("使用说明\n\n查看规则", announcement.content)
    }

    @Test
    fun `word style content rejects unsafe marks and links`() {
        val invalidBlocks = listOf(
            """{"type":"paragraph","text":"危险格式","content":[{"type":"text","text":"危险格式","marks":[{"type":"script"}]}]}""",
            """{"type":"paragraph","text":"危险链接","content":[{"type":"text","text":"危险链接","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}""",
        )

        invalidBlocks.forEachIndexed { index, block ->
            val draft = service.createDraft("announcements", null, 1L)
            val payload = objectMapper.readTree(
                """{"slug":"invalid-word-$index","title":"非法富文本","category":"test","categoryLabel":"测试","summary":"必须拒绝不安全格式","blocks":[$block],"priority":0,"pinned":false}"""
            )
            assertThrows(ParamErrorException::class.java) { service.saveDraft(draft.id, draft.version, payload, 1L) }
        }
    }

    @Test
    fun `draft payload rejects unknown top level fields`() {
        val draft = service.createDraft("announcements", null, 1L)
        val payload = objectMapper.readTree(
            """{"slug":"unknown-field","title":"字段校验","category":"test","categoryLabel":"测试","summary":"只接受前端约定字段","blocks":[],"priority":0,"pinned":false,"unexpected":"reject"}""",
        )

        assertThrows(ParamErrorException::class.java) {
            service.saveDraft(draft.id, draft.version, payload, 1L)
        }
    }

    @Test
    fun `structured word blocks round trip and reject unsafe shapes`() {
        val safeBlocks = objectMapper.readTree(
            """[
              {"id":"heading-1","type":"heading","level":"2","text":"Heading","content":[{"type":"text","text":"Heading","marks":[{"type":"bold"}]}],"align":"center"},
              {"id":"paragraph-1","type":"paragraph","text":"Open the rules","content":[{"type":"text","text":"Open the rules","marks":[{"type":"link","attrs":{"href":"/wiki/rules","target":"_blank"}}]}],"align":"left"},
              {"id":"list-1","type":"bulletList","items":["One",{"text":"Open rules","content":[{"type":"text","text":"Open ","marks":[{"type":"bold"}]},{"type":"text","text":"rules","marks":[{"type":"link","attrs":{"href":"/wiki/rules"}}]}]}]},
              {"id":"quote-1","type":"callout","tone":"info","text":"Remember this","content":[{"type":"text","text":"Remember this"}]},
              {"id":"code-1","type":"code","text":"const markup = \"<section>\";"}
            ]""",
        )
        val draft = service.createDraft("announcements", null, 1L)
        val payload = objectMapper.readTree(
            """{"slug":"structured-blocks","title":"Structured blocks","category":"test","categoryLabel":"Test","summary":"Structured content","blocks":${objectMapper.writeValueAsString(safeBlocks)},"priority":0,"pinned":false}""",
        )

        val saved = service.saveDraft(draft.id, draft.version, payload, 1L)
        assertEquals(safeBlocks, saved.payload.path("blocks"))
        service.publish(saved.id, saved.version, 1L)
        val announcement = requireNotNull(announcements.findBySlug("structured-blocks"))
        assertEquals(safeBlocks, objectMapper.readTree(announcement.contentBlocks))

        val unsafeBlocks = listOf(
            """{"type":"script","text":"alert(1)"}""",
            """{"type":"paragraph","text":"unsafe","html":"<script>alert(1)</script>"}""",
            """{"type":"heading","level":"4","text":"invalid heading"}""",
            """{"type":"bulletList","items":[{"type":"script"}]}""",
            """{"type":"image","src":"javascript:alert(1)","alt":"unsafe image"}""",
            """{"type":"paragraph","content":[{"type":"text","text":"unsafe","marks":[{"type":"link","attrs":{"href":"/safe","onclick":"alert(1)"}}]}]}""",
        )
        unsafeBlocks.forEachIndexed { index, block ->
            val unsafeDraft = service.createDraft("announcements", null, 1L)
            val unsafePayload = objectMapper.readTree(
                """{"slug":"unsafe-shape-$index","title":"Unsafe block","category":"test","categoryLabel":"Test","summary":"Must fail","blocks":[$block],"priority":0,"pinned":false}""",
            )
            assertThrows(ParamErrorException::class.java) {
                service.saveDraft(unsafeDraft.id, unsafeDraft.version, unsafePayload, 1L)
            }
        }
    }

    @Test
    fun `server activity wiki and history all support publish and unpublish`() {
        val cases = listOf(
            ContentCase("servers", "test-server", serverPayload()),
            ContentCase("activities", "test-activity", activityPayload()),
            ContentCase("wiki", "test-wiki", wikiPayload()),
            ContentCase("history", "test-history", historyPayload()),
        )

        cases.forEach { case ->
            val draft = service.createDraft(case.resource, null, 1L)
            val saved = service.saveDraft(draft.id, draft.version, case.payload, 1L)
            val published = service.publish(draft.id, saved.version, 1L)
            val summary = service.list(case.resource).single { it.slug == case.slug }

            assertTrue(summary.published, "${case.resource} should be published")
            assertNotNull(summary.id)

            val resourceId = requireNotNull(published.resourceId)
            service.unpublish(case.resource, resourceId, 1L)
            assertFalse(service.list(case.resource).single { it.slug == case.slug }.published)

            service.deleteResource(case.resource, resourceId, 1L)
            assertFalse(service.list(case.resource).any { it.slug == case.slug })
        }
    }

    private fun announcementPayload(summary: String, text: String) = objectMapper.readTree(
        """{"slug":"flow-test","title":"流程公告","category":"test","categoryLabel":"测试","summary":"$summary","blocks":[{"type":"paragraph","text":"$text"}],"priority":1,"pinned":false,"status":"DRAFT"}""",
    )

    private fun serverPayload() = objectMapper.readTree(
        """{"slug":"test-server","name":"测试服务器","gameplay":"测试玩法","category":"PERMANENT","address":"test.neko.local","version":"1.21.1","pack":"无需整合包","description":"用于测试内容发布","rules":"遵守测试规则","icon":"server","featured":false,"published":false}""",
    )

    private fun activityPayload() = objectMapper.readTree(
        """{"slug":"test-activity","name":"测试活动","kind":"LIMITED","status":"UPCOMING","statusLabel":"即将开始","serverSlug":"event","timeText":"周六 20:00","participation":"直接加入活动服","description":"用于测试活动发布","icon":"event","blocks":[{"type":"paragraph","text":"活动正文"}],"requiresPack":false,"priority":1,"published":false}""",
    )

    private fun wikiPayload() = objectMapper.readTree(
        """{"slug":"test-wiki","title":"测试 Wiki","summary":"用于测试 Wiki 发布","groupName":"测试分组","content":"","icon":"guide","blocks":[{"type":"paragraph","text":"Wiki 正文"}],"linkUrl":"/home","sortOrder":99,"published":false}""",
    )

    private fun historyPayload() = objectMapper.readTree(
        """{"slug":"test-history","title":"测试历史活动","meta":"7 月 20 日 · 活动服","altText":"测试活动封面说明","featured":false,"content":"","blocks":[{"type":"paragraph","text":"活动记录正文"}],"published":false}""",
    )

    private fun mediaAsset(draftId: String? = null, resourceId: Long? = null) = MediaAsset(
        draftId = draftId,
        resourceType = "ANNOUNCEMENT",
        resourceId = resourceId,
        purpose = "COVER",
        originalName = "test.png",
        storageName = "${UUID.randomUUID()}.png",
        mimeType = "image/png",
        sizeBytes = 8,
        width = 1,
        height = 1,
    )

    private data class ContentCase(val resource: String, val slug: String, val payload: tools.jackson.databind.JsonNode)
}
