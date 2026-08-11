package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/** 项目动态数据访问层，提供按项目条目及状态查询。 */
@Repository
interface ObjectItemUpdateRepository : JpaRepository<ObjectItemUpdate, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemUpdate>

    fun findByObjectItemId(objectItemId: Int, pageable: Pageable): Page<ObjectItemUpdate>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemUpdateStatus): List<ObjectItemUpdate>

    fun findByObjectItemIdAndStatus(
        objectItemId: Int,
        status: ObjectItemUpdateStatus,
        pageable: Pageable,
    ): Page<ObjectItemUpdate>

    fun findByStatus(status: ObjectItemUpdateStatus): List<ObjectItemUpdate>

    fun findByStatus(status: ObjectItemUpdateStatus, pageable: Pageable): Page<ObjectItemUpdate>

    fun countByObjectItemId(objectItemId: Int): Long

    fun countByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemUpdateStatus): Long

    fun countByStatus(status: ObjectItemUpdateStatus): Long
}
