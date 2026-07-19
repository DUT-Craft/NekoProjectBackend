package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItem
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Tag
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.TagRepository
import `fun`.utf8.nekoprojectbackend.handlder.ConflictException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.stubbing.OngoingStubbing
import java.time.LocalDateTime
import java.util.Optional

// Kotlin-friendly alias for Mockito.when（when 在 Kotlin 是关键字）
private fun <T> whenever(methodCall: T): OngoingStubbing<T> = Mockito.`when`(methodCall)

private fun tag(
    id: Long,
    name: String,
    parentId: Long? = null,
    selectable: Boolean = true,
    deleted: Boolean = false,
    sortOrder: Int = 0,
): Tag = Tag().apply {
    this.id = id
    this.name = name
    this.normalizedName = name.trim().lowercase()
    this.parentId = parentId
    this.selectable = selectable
    this.sortOrder = sortOrder
    this.createTime = LocalDateTime.now()
    this.updateTime = LocalDateTime.now()
    if (deleted) this.deletedAt = LocalDateTime.now()
}

/** TagService 纯单元测试：标签解析校验、树组装、唯一性、删除防误删分支（不启动 Spring / 不连库）。 */
class TagServiceTest {

    private val tagRepository: TagRepository = Mockito.mock(TagRepository::class.java)
    private val objectItemRepository: ObjectItemRepository = Mockito.mock(ObjectItemRepository::class.java)
    private val service = TagService(tagRepository, objectItemRepository)

    // ---- resolveSelectableTags：项目写入口统一校验 ----

    @Test
    fun `resolveSelectableTags accepts empty list`() {
        assertTrue(service.resolveSelectableTags(emptyList()).isEmpty())
    }

    @Test
    fun `resolveSelectableTags preserves request order and dedups via LinkedHashSet`() {
        whenever(tagRepository.findAllById(any())).thenReturn(listOf(tag(1, "建筑"), tag(2, "长期"), tag(3, "RPG")))
        val resolved = service.resolveSelectableTags(listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), resolved.map { it.id })
    }

    @Test
    fun `resolveSelectableTags rejects more than 10`() {
        val ids = (1L..11L).toList()
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(ids) }
        assertTrue(ex.message.contains("10"))
    }

    @Test
    fun `resolveSelectableTags rejects duplicates`() {
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(listOf(1L, 2L, 1L)) }
        assertTrue(ex.message.contains("重复"))
    }

    @Test
    fun `resolveSelectableTags rejects non-positive ids`() {
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(listOf(0L, 1L)) }
        assertTrue(ex.message.contains("大于 0"))
    }

    @Test
    fun `resolveSelectableTags rejects missing ids with list`() {
        whenever(tagRepository.findAllById(any())).thenReturn(listOf(tag(1, "建筑"))) // 2、3 缺失
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(listOf(1L, 2L, 3L)) }
        assertTrue(ex.message.contains("不存在"))
        assertTrue(ex.message.contains("2"))
        assertTrue(ex.message.contains("3"))
    }

    @Test
    fun `resolveSelectableTags rejects deleted tag`() {
        whenever(tagRepository.findAllById(any())).thenReturn(listOf(tag(1, "建筑", deleted = true)))
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(listOf(1L)) }
        assertTrue(ex.message.contains("已删除"))
    }

    @Test
    fun `resolveSelectableTags rejects non-selectable group tag`() {
        whenever(tagRepository.findAllById(any())).thenReturn(listOf(tag(1, "项目方向", selectable = false)))
        val ex = assertThrows<ParamErrorException> { service.resolveSelectableTags(listOf(1L)) }
        assertTrue(ex.message.contains("分组"))
    }

    // ---- getTree ----

    @Test
    fun `getTree builds hierarchy sorted by sortOrder then id`() {
        val root = tag(1, "项目方向", parentId = null, selectable = false, sortOrder = 0)
        val building = tag(2, "建筑", parentId = 1, sortOrder = 1)
        val survival = tag(3, "生电", parentId = 1, sortOrder = 0)
        val cycle = tag(4, "项目周期", parentId = null, selectable = false, sortOrder = 1)
        whenever(tagRepository.findByDeletedAtIsNull()).thenReturn(listOf(root, building, survival, cycle))

        val tree = service.getTree()
        assertEquals(2, tree.size)
        assertEquals("项目方向", tree[0].name)
        // 子节点按 sortOrder 升序：生电(0) 在 建筑(1) 前
        assertEquals(listOf("生电", "建筑"), tree[0].children.map { it.name })
        assertEquals("项目周期", tree[1].name)
    }

    @Test
    fun `search returns only selectable active tags matching keyword case-insensitively`() {
        whenever(tagRepository.findByDeletedAtIsNull()).thenReturn(
            listOf(
                tag(1, "建筑", selectable = true),
                tag(2, "项目方向", selectable = false),
                tag(3, "RPG", selectable = true),
            )
        )
        val matched = service.search("rpg")
        assertEquals(1, matched.size)
        assertEquals(3L, matched[0].id)
    }

    // ---- create / update 唯一性 ----

    @Test
    fun `create rejects duplicate normalized name ignoring case and trim`() {
        whenever(tagRepository.findByNormalizedNameAndDeletedAtIsNull("rpg")).thenReturn(tag(7, "RPG"))
        val ex = assertThrows<ParamErrorException> { service.create(TagSaveRequest(name = "  rpg  ")) }
        assertTrue(ex.message.contains("已存在"))
    }

    @Test
    fun `create trims name normalizes and persists`() {
        whenever(tagRepository.findByNormalizedNameAndDeletedAtIsNull("建筑")).thenReturn(null)
        whenever(tagRepository.save(any())).thenAnswer { it.getArgument<Tag>(0).apply { id = 42L } }
        val result = service.create(TagSaveRequest(name = "  建筑  ", sortOrder = 2, description = "  建造类 "))
        assertEquals("建筑", result.name)
        assertEquals(2, result.sortOrder)
        assertEquals("建造类", result.description)
        assertFalse(result.deleted)
    }

    @Test
    fun `update rejects moving tag under its own descendant`() {
        // 节点 1（建筑）拟移动到节点 5 下，而 5 是 1 的后代（5→parent 3→parent 1）
        whenever(tagRepository.findById(1L)).thenReturn(Optional.of(tag(1, "建筑")))
        whenever(tagRepository.findById(3L)).thenReturn(Optional.of(tag(3, "x", parentId = 1)))
        whenever(tagRepository.findById(5L)).thenReturn(Optional.of(tag(5, "y", parentId = 3)))
        whenever(tagRepository.findByNormalizedNameAndDeletedAtIsNull("建筑")).thenReturn(tag(1, "建筑"))
        val ex = assertThrows<ParamErrorException> {
            service.update(1L, TagSaveRequest(name = "建筑", parentId = 5L))
        }
        assertTrue(ex.message.contains("后代"))
    }

    // ---- delete 防误删分支 ----

    @Test
    fun `delete refuses when active children exist`() {
        whenever(tagRepository.findById(1L)).thenReturn(Optional.of(tag(1, "项目方向", selectable = false)))
        whenever(tagRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(3L)
        val ex = assertThrows<ConflictException> { service.delete(1L) }
        assertTrue(ex.message.contains("子节点"))
    }

    @Test
    fun `delete detaches projects and soft deletes leaf tag`() {
        val leaf = tag(1, "建筑")
        val project = ObjectItem().apply { id = 10; tags.add(leaf) }
        whenever(tagRepository.findById(1L)).thenReturn(Optional.of(leaf))
        whenever(tagRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L)
        whenever(tagRepository.countProjectsByTagId(1L)).thenReturn(1L)
        whenever(objectItemRepository.findByTagId(1L)).thenReturn(listOf(project))

        val affected = service.delete(1L)
        assertEquals(1L, affected)
        assertTrue(project.tags.isEmpty())
        assertTrue(leaf.deletedAt != null)
    }

    @Test
    fun `create rejects unknown parent`() {
        whenever(tagRepository.findById(99L)).thenReturn(Optional.empty())
        val ex = assertThrows<ResourceNotFoundException> {
            service.create(TagSaveRequest(name = "子标签", parentId = 99L))
        }
        assertTrue(ex.message.contains("父标签"))
    }
}
