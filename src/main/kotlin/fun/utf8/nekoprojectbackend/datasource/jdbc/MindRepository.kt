package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 想法表数据访问层，提供按状态 / MC ID / 标题 / 昵称等查询。 */
@Repository
interface MindRepository : JpaRepository<Mind, Int> {
    fun findByStatus(status: MindStatus): List<Mind>

    fun countByStatus(status: MindStatus): Long

    fun findByStatusIn(statuses: Collection<MindStatus>): List<Mind>

    fun findByMcId(mcId: String): List<Mind>

    fun findByTitleContainingIgnoreCase(title: String): List<Mind>

    fun findByNickNameContainingIgnoreCase(nickName: String): List<Mind>
}
