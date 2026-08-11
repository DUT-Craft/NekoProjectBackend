package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/** 审计日志数据访问层：支持按动作 / 目标类型 / 操作者 / 时间区间分页查询。 */
@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {

    @Query(
        """
        select a from AuditLog a
        where (:action is null or a.action = :action)
          and (:targetType is null or a.targetType = :targetType)
          and (:operatorId is null or a.operatorId = :operatorId)
          and (:fromTime is null or a.time >= :fromTime)
          and (:toTime is null or a.time <= :toTime)
        order by a.time desc
        """
    )
    fun query(
        action: String?,
        targetType: String?,
        operatorId: Long?,
        fromTime: java.time.Instant?,
        toTime: java.time.Instant?,
        pageable: Pageable,
    ): Page<AuditLog>
}
