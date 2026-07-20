package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 全局标签字典实体：自关联父子层级，驱动前端 Cascader 预设。
 *
 * - name 为展示名；normalizedName（trim + 小写）由数据库唯一约束兜底。
 *   软删除时 normalizedName 会改为带 ID 的墓碑值，使原名称可以再次创建。
 * - parentId 非空表示子节点；selectable=false 表示仅用于分组的「不可选」节点（项目不能直接关联）。
 * - deletedAt 非空表示软删除：不再出现在公开树 / 项目选择器 / 搜索中，并解除全部项目关联。
 */
@Entity
@Table(
    name = "tag",
    uniqueConstraints = [UniqueConstraint(name = "uk_tag_normalized_name", columnNames = ["normalized_name"])],
    indexes = [
        Index(name = "idx_tag_parent_sort", columnList = "parent_id, sort_order, id"),
    ],
)
class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "name", nullable = false, length = 32)
    var name: String? = null

    @Column(name = "normalized_name", nullable = false, length = 160)
    var normalizedName: String? = null

    /** 父 Tag ID；为空表示根节点。仅保存 ID，不建立双向 JPA 关系，避免循环序列化与级联复杂度。 */
    @Column(name = "parent_id")
    var parentId: Long? = null

    /** 是否可被项目直接选择；分组节点为 false。 */
    @Column(name = "selectable", nullable = false)
    var selectable: Boolean? = true

    /** 同级排序，小的在前。 */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int? = 0

    @Column(name = "description", length = 255)
    var description: String? = null

    /** 软删除时间；非空表示已删除。 */
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime? = null

    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime? = null
}
