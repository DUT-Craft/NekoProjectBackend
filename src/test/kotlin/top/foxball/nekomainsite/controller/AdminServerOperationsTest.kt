package top.foxball.nekomainsite.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class AdminServerOperationsTest @Autowired constructor(
    private val controller: AdminContentController,
    private val servers: ServerRepository,
    private val audits: AuditLogRepository,
    private val users: UserRepository,
) {
    @Test
    fun `maintenance operations apply immediately and are audited`() {
        val adminId = users.findByUsername("admin")?.id ?: error("local admin missing")
        val authentication = UsernamePasswordAuthenticationToken(
            adminId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        val server = servers.findBySlug("redstone") ?: error("seed server missing")
        val serverId = requireNotNull(server.id)

        controller.setServerMaintenance(
            authentication,
            serverId,
            AdminContentController.ServerMaintenanceRequest(true),
        )
        val maintenance = servers.findById(serverId).orElseThrow()
        assertTrue(maintenance.maintenance)
        assertEquals(ServerStatus.MAINTENANCE, maintenance.status)
        assertEquals("维护中", maintenance.statusLabel)
        assertEquals(0, maintenance.onlineCount)

        controller.setServerMaintenance(
            authentication,
            serverId,
            AdminContentController.ServerMaintenanceRequest(false),
        )
        val available = servers.findById(serverId).orElseThrow()
        assertFalse(available.maintenance)
        assertEquals(ServerStatus.AVAILABLE, available.status)
        assertEquals("待检测", available.statusLabel)
        assertEquals(0, available.onlineCount)

        available.address = "status-test.invalid"
        available.onlineCount = 9
        servers.save(available)
        controller.refreshServer(authentication, serverId)
        val offline = servers.findById(serverId).orElseThrow()
        assertEquals(ServerStatus.OFFLINE, offline.status)
        assertEquals(0, offline.onlineCount)

        val actions = audits.findAllByOrderByCreatedAtDesc()
            .filter { it.resourceType == "SERVER" && it.resourceId == serverId.toString() }
            .map { it.action }
        assertTrue(actions.containsAll(listOf("MAINTENANCE_START", "MAINTENANCE_END", "STATUS_REFRESH")))
    }
}
