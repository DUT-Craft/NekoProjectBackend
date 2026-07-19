package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.Tag
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.TagRepository
import `fun`.utf8.nekoprojectbackend.handlder.ConflictException
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.handlder.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Locale

/** Tag 摘要：项目响应里携带的轻量标签信息（仅公开字段）。 */
data class TagSummaryResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
)

/** Tag 树节点：驱动前端 Cascader。 */
data class TagTreeResponse(
    val id: Long,
    val name: String,
    val selectable: Boolean,
    val sortOrder: Int,
    val children: List<TagTreeResponse>,
)

/** Tag 新增 / 修改请求（管理端）。 */
data class TagSaveRequest(
    val name: String = "",
    val parentId: Long? = null,
    val selectable: Boolean = true,
    val sortOrder: Int = 0,
    val description: String? = null,
)

/** Tag 管理端列表项：含关联项目数与软删除状态。 */
data class TagAdminResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val selectable: Boolean,
    val sortOrder: Int,
    val description: String?,
    val projectCount: Long,
    val deleted: Boolean,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

/**
 * 全局标签字典业务：公开 Tag 树 / 搜索、管理端 CRUD、以及项目写入口统一复用的标签解析校验。
 *
 * 仅 [fun].utf8.nekoprojectbackend.controller.AdminTagController 的写接口会调用 create/update/delete，
 * 并由 [AccessService.requireSuperAdmin] 兜底权限；本服务聚焦数据与一致性。
 */
@Service
class TagService(
    private val tagRepository: TagRepository,
    private val objectItemRepository: ObjectItemRepository,
) {

    /** 公开 Tag 树：仅活跃节点，组装为父子层级，按 sortOrder / id 排序。 */
    @Transactional(readOnly = true)
    fun getTree(): List<TagTreeResponse> = buildTree(tagRepository.findByDeletedAtIsNull())

    /** 公开扁平搜索：仅可选活跃节点，按名称包含命中（忽略大小写）。 */
    @Transactional(readOnly = true)
    fun search(keyword: String?): List<TagSummaryResponse> {
        val kw = keyword?.trim().orEmpty()
        val source = tagRepository.findByDeletedAtIsNull().filter { it.selectable == true }
        val matched = if (kw.isEmpty()) source else source.filter { it.name?.contains(kw, ignoreCase = true) == true }
        return matched.sortedWith(compareBy({ it.sortOrder ?: 0 }, { it.id }))
            .map { TagSummaryResponse(it.id!!, it.name ?: "", it.parentId) }
    }

    /** 管理端列表：含已删除节点与每个 Tag 的关联项目数，平铺返回（parentId 供前端组树）。 */
    @Transactional(readOnly = true)
    fun listForAdmin(): List<TagAdminResponse> {
        val all = tagRepository.findAll()
            .sortedWith(compareBy({ it.parentId }, { it.sortOrder ?: 0 }, { it.id }))
        val counts = tagRepository.countProjectsGroupedByTag(all.mapNotNull { it.id })
            .associate { it.tagId to it.projectCount }
        return all.map { toAdmin(it, counts[it.id] ?: 0L) }
    }

    @Transactional
    fun create(request: TagSaveRequest): TagAdminResponse {
        val name = requireTagName(request.name)
        val parentId = request.parentId?.let { requireParentId(it) }
        ensureNameAvailable(name, excludeId = null)
        val now = LocalDateTime.now()
        val tag = Tag().apply {
            this.name = name
            this.normalizedName = normalize(name)
            this.parentId = parentId
            this.selectable = request.selectable
            this.sortOrder = request.sortOrder
            this.description = normalizeDescription(request.description)
            this.createTime = now
            this.updateTime = now
        }
        return toAdmin(tagRepository.save(tag), 0L)
    }

    @Transactional
    fun update(id: Long, request: TagSaveRequest): TagAdminResponse {
        val tag = findTag(id)
        if (tag.deletedAt != null) {
            throw ParamErrorException("标签已删除，不能修改")
        }
        val name = requireTagName(request.name)
        val newParentId = request.parentId
        if (newParentId != null && newParentId != tag.parentId) {
            if (isAncestorOrSelf(candidateAncestor = id, nodeId = newParentId)) {
                throw ParamErrorException("不能把标签移动到自己或其后代节点下")
            }
            requireParentId(newParentId)
        }
        ensureNameAvailable(name, excludeId = id)
        tag.name = name
        tag.normalizedName = normalize(name)
        tag.parentId = newParentId
        tag.selectable = request.selectable
        tag.sortOrder = request.sortOrder
        tag.description = normalizeDescription(request.description)
        tag.updateTime = LocalDateTime.now()
        return toAdmin(tagRepository.save(tag), tagRepository.countProjectsByTagId(id))
    }

    /**
     * 软删除 Tag 并解除全部项目关联。存在活跃子节点时拒绝（409），避免误删整条分支。
     * 返回被解除关联的项目数，供调用方回显「受影响范围」。
     */
    @Transactional
    fun delete(id: Long): Long {
        val tag = findTag(id)
        if (tag.deletedAt != null) {
            // 已删除：幂等返回 0
            return 0L
        }
        val activeChildren = tagRepository.countByParentIdAndDeletedAtIsNull(id)
        if (activeChildren > 0) {
            throw ConflictException("该标签下还有 $activeChildren 个子节点，请先处理子节点后再删除")
        }
        val affected = tagRepository.countProjectsByTagId(id)
        // 解除关联：通过实体集合移除，确保持久化上下文一致（同一事务内同一 PC，引用相等）
        objectItemRepository.findByTagId(id).forEach { project -> project.tags.remove(tag) }
        val now = LocalDateTime.now()
        tag.deletedAt = now
        tag.updateTime = now
        tagRepository.save(tag)
        return affected
    }

    /**
     * 项目写入口统一调用的标签解析与校验（见方案 §5.6）。
     *
     * 校验顺序：数量 ≤10 → ID 为正 → 不重复 → 全部存在 → 未删除且可选用。
     * 返回按请求顺序组织的 [LinkedHashSet]，表达项目内标签不重复的领域约束。
     */
    @Transactional(readOnly = true)
    fun resolveSelectableTags(tagIds: List<Long>): LinkedHashSet<Tag> {
        if (tagIds.size > MAX_TAGS_PER_PROJECT) {
            throw ParamErrorException("项目最多只能选择 $MAX_TAGS_PER_PROJECT 个标签")
        }
        val positiveIds = tagIds.map { id ->
            if (id <= 0L) throw ParamErrorException("标签 ID 必须大于 0")
            id
        }
        if (positiveIds.toSet().size != positiveIds.size) {
            throw ParamErrorException("项目标签不能重复")
        }
        val byId = tagRepository.findAllById(positiveIds).associateBy { it.id!! }
        val missing = positiveIds.filter { byId[it] == null }
        if (missing.isNotEmpty()) {
            throw ParamErrorException("标签不存在：${missing.joinToString(", ")}")
        }
        val resolved = LinkedHashSet<Tag>()
        for (id in positiveIds) {
            val tag = byId.getValue(id)
            if (tag.deletedAt != null) {
                throw ParamErrorException("标签「${tag.name}」已删除，不能再使用")
            }
            if (tag.selectable != true) {
                throw ParamErrorException("标签「${tag.name}」仅用于分组，不能直接选择")
            }
            resolved.add(tag)
        }
        return resolved
    }

    /** 仅活跃标签名 → 实体，供数据迁移 / 播种按名称复用既有 Tag。 */
    @Transactional(readOnly = true)
    fun findActiveByName(name: String): Tag? =
        tagRepository.findByNormalizedNameAndDeletedAtIsNull(normalize(name))

    private fun buildTree(all: List<Tag>): List<TagTreeResponse> {
        val childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId!! }

        fun build(tag: Tag): TagTreeResponse {
            val children = (childrenByParent[tag.id] ?: emptyList())
                .sortedWith(compareBy({ it.sortOrder ?: 0 }, { it.id }))
                .map { build(it) }
            return TagTreeResponse(
                id = tag.id!!,
                name = tag.name ?: "",
                selectable = tag.selectable ?: true,
                sortOrder = tag.sortOrder ?: 0,
                children = children,
            )
        }

        return all.filter { it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder ?: 0 }, { it.id }))
            .map { build(it) }
    }

    /** 自下而上回溯 nodeId 的祖先链，判断 candidateAncestor 是否为其祖先或自身（用于移动时防环）。 */
    private fun isAncestorOrSelf(candidateAncestor: Long, nodeId: Long): Boolean {
        var current: Long? = nodeId
        val seen = HashSet<Long>()
        while (current != null && seen.add(current)) {
            if (current == candidateAncestor) return true
            current = tagRepository.findById(current)
                .orElse(null)
                ?.takeIf { it.deletedAt == null }
                ?.parentId
        }
        return false
    }

    private fun ensureNameAvailable(name: String, excludeId: Long?) {
        tagRepository.findByNormalizedNameAndDeletedAtIsNull(normalize(name))?.let { existing ->
            if (existing.id != excludeId) {
                throw ParamErrorException("标签名「${existing.name}」已存在")
            }
        }
    }

    private fun requireParentId(parentId: Long): Long {
        val parent = tagRepository.findById(parentId)
            .orElseThrow { ResourceNotFoundException("父标签不存在") }
        if (parent.deletedAt != null) {
            throw ParamErrorException("父标签已删除")
        }
        return parentId
    }

    private fun requireTagName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            throw ParamErrorException("标签名不能为空")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            throw ParamErrorException("标签名不能超过 $MAX_NAME_LENGTH 个字符")
        }
        return trimmed
    }

    private fun normalizeDescription(raw: String?): String? = raw?.trim()?.ifBlank { null }

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    private fun findTag(id: Long): Tag =
        tagRepository.findById(id).orElseThrow { ResourceNotFoundException("标签不存在") }

    private fun toAdmin(tag: Tag, projectCount: Long) = TagAdminResponse(
        id = tag.id!!,
        name = tag.name ?: "",
        parentId = tag.parentId,
        selectable = tag.selectable ?: true,
        sortOrder = tag.sortOrder ?: 0,
        description = tag.description,
        projectCount = projectCount,
        deleted = tag.deletedAt != null,
        createTime = tag.createTime,
        updateTime = tag.updateTime,
    )

    private companion object {
        const val MAX_NAME_LENGTH = 32
        const val MAX_TAGS_PER_PROJECT = 10
    }
}
