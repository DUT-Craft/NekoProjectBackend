package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ObjectItemUpdateRepository : JpaRepository<ObjectItemUpdate, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemUpdate>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemUpdateStatus): List<ObjectItemUpdate>

    fun findByStatus(status: ObjectItemUpdateStatus): List<ObjectItemUpdate>
}
