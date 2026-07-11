package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** 邀请码数据访问层。 */
@Repository
interface InviteCodeRepository : JpaRepository<InviteCode, Long> {
    fun findByCode(code: String): InviteCode?

    /**
     * 原子消费：仅当状态为 [InviteCodeStatus.UNUSED] 且未过期时置 [InviteCodeStatus.USED] 并记录消费者。
     * 返回受影响行数（1 = 消费成功，0 = 不存在 / 已用 / 已过期）。靠 `status = UNUSED` 谓词 + 行锁保证一码一次。
     */
    @Modifying(flushAutomatically = true)
    @Query(
        "UPDATE InviteCode ic SET ic.status = :used, ic.usedBy = :usedBy, ic.usedAt = :now " +
                "WHERE ic.code = :code AND ic.status = :unused AND ic.expiresAt > :now",
    )
    fun consume(
        @Param("code") code: String,
        @Param("usedBy") usedBy: Long,
        @Param("now") now: LocalDateTime,
        @Param("unused") unused: InviteCodeStatus,
        @Param("used") used: InviteCodeStatus,
    ): Int

    /** 懒清理：把已过期但仍标记 UNUSED 的邀请码置为 EXPIRED，供历史列表展示真实状态。 */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE InviteCode ic SET ic.status = :expired WHERE ic.status = :unused AND ic.expiresAt <= :now")
    fun markExpired(
        @Param("now") now: LocalDateTime,
        @Param("unused") unused: InviteCodeStatus,
        @Param("expired") expired: InviteCodeStatus,
    ): Int
}
