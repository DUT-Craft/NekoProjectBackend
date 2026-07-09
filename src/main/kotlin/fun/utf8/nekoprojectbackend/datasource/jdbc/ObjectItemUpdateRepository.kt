package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 项目动态数据访问层，提供按项目条目及状态查询。 */
@Repository
interface ObjectItemUpdateRepository : JpaRepository<ObjectItemUpdate, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemUpdate>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemUpdateStatus): List<ObjectItemUpdate>

    fun findByStatus(status: ObjectItemUpdateStatus): List<ObjectItemUpdate>
}
