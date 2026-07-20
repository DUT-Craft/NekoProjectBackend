package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/** 全局标签字典数据访问层。 */
@Repository
interface TagRepository : JpaRepository<Tag, Long> {
    /** 全部活跃（未软删除）Tag。 */
    fun findByDeletedAtIsNull(): List<Tag>

    /** 按标准化名称查找活跃 Tag，用于唯一性校验。 */
    fun findByNormalizedNameAndDeletedAtIsNull(normalizedName: String): Tag?

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tag t where t.id in ?1 order by t.id")
    fun findAllByIdForShare(ids: Collection<Long>): List<Tag>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tag t where t.deletedAt is null order by t.id")
    fun findAllActiveForUpdate(): List<Tag>

    /** 统计活跃子节点数量，删除父节点前用以阻止误删整条分支。 */
    fun countByParentIdAndDeletedAtIsNull(parentId: Long): Long

    /** 单个 Tag 当前关联的项目数（用于删除前提示受影响范围）。跨 object_item_tag 关联表计数。 */
    @Query("select count(o.id) from ObjectItem o join o.tags t where t.id = ?1")
    fun countProjectsByTagId(tagId: Long): Long

    /** 批量统计多个 Tag 的关联项目数（管理端列表展示 projectCount）。 */
    @Query(
        "select t.id as tagId, count(o.id) as projectCount " +
            "from ObjectItem o join o.tags t where t.id in ?1 group by t.id"
    )
    fun countProjectsGroupedByTag(tagIds: Collection<Long>): List<TagProjectCount>
}

/** Tag 关联项目数投影（接口投影，类型安全）。 */
interface TagProjectCount {
    val tagId: Long
    val projectCount: Long
}
