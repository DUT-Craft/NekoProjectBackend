package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MindRepository : JpaRepository<Mind, Int> {
    fun findByStatus(status: MindStatus): List<Mind>

    fun countByStatus(status: MindStatus): Long

    fun findByStatusIn(statuses: Collection<MindStatus>): List<Mind>

    fun findByMcId(mcId: String): List<Mind>

    fun findByTitleContainingIgnoreCase(title: String): List<Mind>

    fun findByNickNameContainingIgnoreCase(nickName: String): List<Mind>
}
