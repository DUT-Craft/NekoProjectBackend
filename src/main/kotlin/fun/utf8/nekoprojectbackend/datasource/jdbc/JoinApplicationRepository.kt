package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/** 加入申请数据访问层，提供按项目条目及状态查询。 */
@Repository
interface JoinApplicationRepository : JpaRepository<JoinApplication, Int> {
    fun findByObjectItemId(objectItemId: Int): List<JoinApplication>

    fun findByObjectItemId(objectItemId: Int, pageable: Pageable): Page<JoinApplication>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: JoinApplicationStatus): List<JoinApplication>

    fun findByObjectItemIdAndStatus(
        objectItemId: Int,
        status: JoinApplicationStatus,
        pageable: Pageable,
    ): Page<JoinApplication>

    fun countByObjectItemId(objectItemId: Int): Long

    fun countByObjectItemIdAndStatus(objectItemId: Int, status: JoinApplicationStatus): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from JoinApplication application where application.id = :id")
    fun findByIdForUpdate(@Param("id") id: Int): JoinApplication?
}
