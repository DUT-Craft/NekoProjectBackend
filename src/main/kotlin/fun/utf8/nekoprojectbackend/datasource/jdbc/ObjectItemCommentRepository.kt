package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 项目评论数据访问层，提供按项目条目及状态查询。 */
@Repository
interface ObjectItemCommentRepository : JpaRepository<ObjectItemComment, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemComment>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemCommentStatus): List<ObjectItemComment>

    fun findByStatus(status: ObjectItemCommentStatus): List<ObjectItemComment>
}
