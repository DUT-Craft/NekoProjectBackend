package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** 文件记录数据访问层。 */
@Repository
interface FileRecordRepository : JpaRepository<FileRecord, Long> {
    fun findByStoredName(storedName: String): FileRecord?

    fun findByUploaderIdOrderByCreateTimeDesc(uploaderId: Long, pageable: Pageable): Page<FileRecord>
}
