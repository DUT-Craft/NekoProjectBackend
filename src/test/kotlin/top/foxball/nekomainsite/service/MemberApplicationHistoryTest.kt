package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.Application
import top.foxball.nekomainsite.entity.jdbc.ModerationStatus
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.handlder.UnauthorizedException
import top.foxball.nekomainsite.repository.ApplicationRepository
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class MemberApplicationHistoryTest @Autowired constructor(
    private val service: SiteContentService,
    private val applications: ApplicationRepository,
    private val users: UserRepository,
) {
    @Test
    fun `members only see their own application status and admin reply`() {
        val firstUser = users.save(User(username = "history-user-1", email = "history-1@example.test", password = "test"))
        val secondUser = users.save(User(username = "history-user-2", email = "history-2@example.test", password = "test"))
        applications.save(
            Application(
                userId = requireNotNull(firstUser.id),
                name = "第一个成员",
                qq = "10001",
                status = ModerationStatus.ADOPTED,
                adminNote = "申请已通过，请查看群内开服安排。",
            ),
        )
        applications.save(Application(userId = requireNotNull(secondUser.id), name = "其他成员", qq = "10002"))

        val result = service.memberApplications(firstUser.id)

        assertEquals(1, result.size)
        assertEquals("第一个成员", result.single().name)
        assertEquals("ADOPTED", result.single().status)
        assertEquals("申请已通过，请查看群内开服安排。", result.single().adminNote)
        assertThrows(UnauthorizedException::class.java) { service.memberApplications(null) }
    }
}
