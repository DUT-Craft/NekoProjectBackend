package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository

/**
 * 项目条目数据访问层。
 *
 * 继承 [JpaSpecificationExecutor] 以支持关键字 / 标签 / 状态等动态组合的数据库侧分页过滤，
 * 替代旧的 findAll() → 内存筛选。标签筛选经 object_item_tag → tag 关联子查询实现。
 */
@Repository
interface ObjectItemRepository : JpaRepository<ObjectItem, Int>, JpaSpecificationExecutor<ObjectItem> {
    fun findByStatus(status: ObjectItemStatus): List<ObjectItem>

    fun countByStatus(status: ObjectItemStatus): Long

    fun findByLeaderMcId(leaderMcId: String): List<ObjectItem>

    fun findByTitleContainingIgnoreCase(title: String): List<ObjectItem>

    fun countByOwnerId(ownerId: Long): Long

    fun findByOwnerId(ownerId: Long): List<ObjectItem>

    /** 关联了指定标签的全部项目（供软删除标签时解除关联）。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from object_item_tag where tag_id = ?1", nativeQuery = true)
    fun deleteTagAssociations(tagId: Long): Int
}
