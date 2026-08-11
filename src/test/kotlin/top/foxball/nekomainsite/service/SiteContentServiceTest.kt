package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.Activity
import top.foxball.nekomainsite.entity.jdbc.ActivityKind
import top.foxball.nekomainsite.entity.jdbc.ActivityStatus
import top.foxball.nekomainsite.entity.jdbc.ApplicationKind
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerCategory
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.repository.ActivityRepository
import top.foxball.nekomainsite.repository.ApplicationRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class SiteContentServiceTest @Autowired constructor(
    private val service: SiteContentService,
    private val activities: ActivityRepository,
    private val applications: ApplicationRepository,
    private val servers: ServerRepository,
    private val users: UserRepository,
) {
    @Test
    fun `application kinds enforce every backend required field`() {
        val userId = users.findByUsername("admin")?.id ?: error("local admin missing")
        val before = applications.count()
        val valid = ApplicationCommand(
            kind = ApplicationKind.SKIN,
            name = "测试成员",
            studentId = "20260001",
            qq = "12345678",
            minecraftId = "NekoPlayer",
            reason = null,
            participantCount = "8 人",
            purpose = "小游戏",
            expectedTime = "本周六晚",
            requirements = "无",
            availableTime = "周末下午",
            skill = "新人引导",
        )
        val invalid = listOf(
            valid.copy(name = " "),
            valid.copy(qq = "12ab"),
            valid.copy(studentId = " "),
            valid.copy(minecraftId = null),
            valid.copy(minecraftId = "bad-id!"),
            valid.copy(kind = ApplicationKind.SERVER, participantCount = null),
            valid.copy(kind = ApplicationKind.SERVER, purpose = " "),
            valid.copy(kind = ApplicationKind.SERVER, expectedTime = null),
            valid.copy(kind = ApplicationKind.SERVER, requirements = " "),
            valid.copy(kind = ApplicationKind.DUTY, availableTime = null),
            valid.copy(kind = ApplicationKind.DUTY, skill = " "),
        )

        invalid.forEach { command ->
            assertThrows(ParamErrorException::class.java) { service.submitApplication(userId, command) }
        }
        assertEquals(before, applications.count())
    }

    @Test
    fun `valid applications are normalized and persisted for every kind`() {
        val userId = users.findByUsername("admin")?.id ?: error("local admin missing")
        val commands = listOf(
            ApplicationCommand(
                ApplicationKind.SKIN, "  邀请码成员  ", "  20260001  ", "12345678", "  NekoPlayer  ",
                null, null, null, null, null, null, null,
            ),
            ApplicationCommand(
                ApplicationKind.SERVER, "开服成员", null, "23456789", null,
                null, " 8 人 ", " 小游戏 ", " 本周六晚 ", " 无 ", null, null,
            ),
            ApplicationCommand(
                ApplicationKind.DUTY, "值班成员", null, "34567890", null,
                " 乐意参与 ", null, null, null, null, " 周末下午 ", " 新人引导 ",
            ),
        )

        val saved = commands.map { applications.findById(service.submitApplication(userId, it)).orElseThrow() }

        assertEquals(listOf(ApplicationKind.SKIN, ApplicationKind.SERVER, ApplicationKind.DUTY), saved.map { it.kind })
        assertEquals("邀请码成员", saved[0].name)
        assertEquals("20260001", saved[0].studentId)
        assertEquals("NekoPlayer", saved[0].minecraftId)
        assertEquals("8 人", saved[1].participantCount)
        assertEquals("小游戏", saved[1].purpose)
        assertEquals("周末下午", saved[2].availableTime)
        assertEquals("新人引导", saved[2].skill)
    }

    @Test
    fun `home content keeps every published server and prioritizes active weekly then long term activities`() {
        servers.saveAll(listOf(
            server("contract-online", ServerCategory.PERMANENT, ServerStatus.ONLINE, online = 6),
            server("contract-offline", ServerCategory.PERMANENT, ServerStatus.OFFLINE),
            server("contract-maintenance", ServerCategory.PERMANENT, ServerStatus.MAINTENANCE),
            server("contract-activity", ServerCategory.ACTIVITY, ServerStatus.AVAILABLE),
        ))
        activities.saveAll(listOf(
            activity("contract-weekly-upcoming", ActivityKind.WEEKLY, ActivityStatus.UPCOMING, priority = 1),
            activity("contract-long-term", ActivityKind.LONG_TERM, ActivityStatus.ONGOING, priority = 999),
            activity("contract-limited", ActivityKind.LIMITED, ActivityStatus.UPCOMING, priority = 999),
            activity("contract-weekly-paused", ActivityKind.WEEKLY, ActivityStatus.PAUSED, priority = 999),
        ))

        val publicServers = service.servers()
        val publicActivities = service.activities().map { it.id }
        val position = publicActivities.withIndex().associate { it.value to it.index }

        assertEquals(
            setOf("contract-online", "contract-offline", "contract-maintenance", "contract-activity"),
            publicServers.map { it.id }.filter { it.startsWith("contract-") }.toSet(),
        )
        assertTrue(publicServers.indexOfFirst { it.id == "contract-online" } < publicServers.indexOfFirst { it.id == "contract-offline" })
        assertTrue(publicServers.indexOfFirst { it.id == "contract-online" } < publicServers.indexOfFirst { it.id == "contract-maintenance" })
        assertEquals("online", publicServers.single { it.id == "contract-online" }.status)
        assertEquals("maintenance", publicServers.single { it.id == "contract-maintenance" }.status)
        assertTrue(position.getValue("contract-weekly-upcoming") < position.getValue("contract-long-term"))
        assertTrue(position.getValue("contract-long-term") < position.getValue("contract-limited"))
        assertTrue(position.getValue("contract-limited") < position.getValue("contract-weekly-paused"))
    }

    private fun server(slug: String, category: ServerCategory, status: ServerStatus, online: Int = 0) = Server(
        slug = slug,
        name = slug,
        gameplay = "contract",
        category = category,
        status = status,
        statusLabel = status.name,
        onlineCount = online,
        capacity = 20,
        address = "$slug.example.test",
        version = "1.21.1",
        pack = "none",
        description = "contract server",
        rules = "rules",
        published = true,
    )

    private fun activity(slug: String, kind: ActivityKind, status: ActivityStatus, priority: Int) = Activity(
        slug = slug,
        name = slug,
        kind = kind,
        status = status,
        statusLabel = status.name,
        serverSlug = "event",
        timeText = "time",
        participation = "join",
        description = "contract activity",
        priority = priority,
        published = true,
    )
}
