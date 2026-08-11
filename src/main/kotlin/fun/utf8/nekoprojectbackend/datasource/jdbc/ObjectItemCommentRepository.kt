package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/** 项目评论数据访问层，提供按项目条目及状态查询。 */
@Repository
interface ObjectItemCommentRepository : JpaRepository<ObjectItemComment, Int> {
    fun findByObjectItemId(objectItemId: Int): List<ObjectItemComment>

    fun findByObjectItemId(objectItemId: Int, pageable: Pageable): Page<ObjectItemComment>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemCommentStatus): List<ObjectItemComment>

    fun findByObjectItemIdAndStatus(
        objectItemId: Int,
        status: ObjectItemCommentStatus,
        pageable: Pageable,
    ): Page<ObjectItemComment>

    fun findByStatus(status: ObjectItemCommentStatus): List<ObjectItemComment>

    fun findByStatus(status: ObjectItemCommentStatus, pageable: Pageable): Page<ObjectItemComment>

    fun countByObjectItemId(objectItemId: Int): Long

    fun countByObjectItemIdAndStatus(objectItemId: Int, status: ObjectItemCommentStatus): Long

    fun countByStatus(status: ObjectItemCommentStatus): Long
}
