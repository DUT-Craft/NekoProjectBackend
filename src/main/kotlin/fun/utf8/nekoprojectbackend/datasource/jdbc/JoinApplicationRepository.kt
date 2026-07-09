package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 加入申请数据访问层，提供按项目条目及状态查询。 */
@Repository
interface JoinApplicationRepository : JpaRepository<JoinApplication, Int> {
    fun findByObjectItemId(objectItemId: Int): List<JoinApplication>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: JoinApplicationStatus): List<JoinApplication>
}
