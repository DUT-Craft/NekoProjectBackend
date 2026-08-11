package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

/** 项目条目数据访问层，提供按状态 / 类型 / 负责人 MC ID / 标题等查询。 */
@Repository
interface ObjectItemRepository : JpaRepository<ObjectItem, Int>, JpaSpecificationExecutor<ObjectItem> {
    fun findByStatus(status: ObjectItemStatus): List<ObjectItem>

    fun countByStatus(status: ObjectItemStatus): Long

    fun countByStatusIn(statuses: Collection<ObjectItemStatus>): Long

    fun findByType(type: String): List<ObjectItem>

    fun findByLeaderMcId(leaderMcId: String): List<ObjectItem>

    fun findByTitleContainingIgnoreCase(title: String): List<ObjectItem>

    fun countByOwnerIdAndStatusNot(ownerId: Long, status: ObjectItemStatus): Long

    fun findByOwnerId(ownerId: Long): List<ObjectItem>
}
