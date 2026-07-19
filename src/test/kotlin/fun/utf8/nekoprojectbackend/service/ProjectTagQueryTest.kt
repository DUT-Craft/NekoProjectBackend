package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.ObjectItemStatus
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** 项目查询相关纯逻辑测试：标签匹配方式解析、公开状态集合。 */
class ProjectTagQueryTest {

    @Test
    fun `TagMatch parses ANY and ALL case-insensitively`() {
        assertEquals(TagMatch.ANY, TagMatch.from("any"))
        assertEquals(TagMatch.ALL, TagMatch.from("ALL"))
        assertEquals(TagMatch.ANY, TagMatch.from("Any"))
    }

    @Test
    fun `TagMatch null stays null so controller can default to ANY`() {
        assertNull(TagMatch.from(null))
    }

    @Test
    fun `TagMatch invalid value throws for a 400 response`() {
        val ex = assertThrows<ParamErrorException> { TagMatch.from("maybe") }
        assertTrue(ex.message.contains("ANY / ALL"))
    }

    @Test
    fun `public statuses include APPROVED and exclude review-gate and deleted states`() {
        assertTrue(ObjectItemStatus.APPROVED in PUBLIC_STATUSES)
        assertTrue(ObjectItemStatus.RECRUITING in PUBLIC_STATUSES)
        assertFalse(ObjectItemStatus.PENDING in PUBLIC_STATUSES)
        assertFalse(ObjectItemStatus.REJECTED in PUBLIC_STATUSES)
        assertFalse(ObjectItemStatus.DELETED in PUBLIC_STATUSES)
        assertEquals(5, PUBLIC_STATUSES.size)
    }
}
