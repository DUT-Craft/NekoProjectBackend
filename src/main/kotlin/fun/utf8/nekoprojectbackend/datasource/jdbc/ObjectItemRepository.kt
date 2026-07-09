package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ObjectItemRepository : JpaRepository<ObjectItem, Int> {
    fun findByStatus(status: ObjectItemStatus): List<ObjectItem>

    fun countByStatus(status: ObjectItemStatus): Long

    fun findByType(type: String): List<ObjectItem>

    fun findByLeaderMcId(leaderMcId: String): List<ObjectItem>

    fun findByTitleContainingIgnoreCase(title: String): List<ObjectItem>
}
