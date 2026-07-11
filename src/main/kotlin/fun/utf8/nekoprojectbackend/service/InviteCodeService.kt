package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.InviteCode
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.InviteCodeRepository
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.InviteCodeStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 项目管理邀请码（持久化到 DB）。
 *
 * - [generate]：总管理触发，生成一张 UNUSED 邀请码并落库（带过期时间 `neko.invite.ttl-seconds`）；
 * - [consume]：原子 UPDATE 保证「一码一次」——仅当 UNUSED 且未过期时置 USED 并记录消费者；
 * - [list]：列出全部历史邀请码（先懒清理过期态，再按生成时间倒序）。
 *
 * 一次性语义靠 UPDATE 的 `status = UNUSED` 谓词 + 数据库行锁，无需 Redis。
 */
@Service
class InviteCodeService(
    private val inviteCodeRepository: InviteCodeRepository,
    @Value("\${neko.invite.ttl-seconds:604800}") private val ttlSeconds: Long,
) {
    @Transactional
    fun generate(createdBy: Long): InviteCode {
        val now = LocalDateTime.now()
        return inviteCodeRepository.save(
            InviteCode(
                code = UUID.randomUUID().toString().replace("-", ""),
                createdBy = createdBy,
                createdAt = now,
                expiresAt = now.plusSeconds(ttlSeconds.coerceAtLeast(1)),
                status = InviteCodeStatus.UNUSED,
            ),
        )
    }

    /** 原子消费：有效（UNUSED 且未过期）则置 USED 并记录消费者，返回 true；否则返回 false。 */
    @Transactional
    fun consume(code: String, usedBy: Long): Boolean {
        if (code.isBlank()) {
            return false
        }
        val now = LocalDateTime.now()
        val affected = inviteCodeRepository.consume(
            code = code.trim(),
            usedBy = usedBy,
            now = now,
            unused = InviteCodeStatus.UNUSED,
            used = InviteCodeStatus.USED,
        )
        return affected > 0
    }

    /** 列出全部历史邀请码：先清理过期态，再按生成时间倒序。 */
    @Transactional
    fun list(): List<InviteCode> {
        inviteCodeRepository.markExpired(
            now = LocalDateTime.now(),
            unused = InviteCodeStatus.UNUSED,
            expired = InviteCodeStatus.EXPIRED,
        )
        return inviteCodeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
    }
}
