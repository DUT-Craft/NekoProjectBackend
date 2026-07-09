package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ObjectItemCommentRepository : JpaRepository<ObjectItemComment, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemComment>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemCommentStatus): List<ObjectItemComment>

    fun findByStatus(status: ObjectItemCommentStatus): List<ObjectItemComment>
}
