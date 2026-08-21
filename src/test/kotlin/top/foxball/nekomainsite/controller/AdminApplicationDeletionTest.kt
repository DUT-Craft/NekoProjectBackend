package top.foxball.nekomainsite.controller

import jakarta.servlet.Filter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import top.foxball.nekomainsite.entity.jdbc.Application
import top.foxball.nekomainsite.repository.ApplicationRepository
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class AdminApplicationDeletionTest @Autowired constructor(
    private val controller: AdminContentController,
    private val applications: ApplicationRepository,
    private val audits: AuditLogRepository,
    private val users: UserRepository,
    private val context: WebApplicationContext,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        val securityFilter = context.getBean("springSecurityFilterChain", Filter::class.java)
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(securityFilter)
            .build()
    }

    @Test
    fun `administrator can permanently delete an application with audit record`() {
        val admin = users.findByUsername("admin") ?: error("local admin missing")
        val authentication = UsernamePasswordAuthenticationToken(
            requireNotNull(admin.id),
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        val saved = applications.save(Application(name = "待删除申请", qq = "10000"))
        val id = requireNotNull(saved.id)

        controller.deleteApplication(authentication, id)

        assertFalse(applications.existsById(id))
        assertTrue(
            audits.findAllByOrderByCreatedAtDesc().any {
                it.resourceType == "APPLICATION" && it.resourceId == id.toString() && it.action == "DELETE"
            },
        )
    }

    @Test
    fun `anonymous and ordinary members cannot delete applications`() {
        val saved = applications.save(Application(name = "受保护申请", qq = "10001"))
        val id = requireNotNull(saved.id)

        mockMvc.perform(delete("/api/admin/applications/{id}", id))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))

        val memberAuthentication = UsernamePasswordAuthenticationToken(
            999L,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        mockMvc.perform(delete("/api/admin/applications/{id}", id).with(authentication(memberAuthentication)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))

        assertTrue(applications.existsById(id))
    }

    @Test
    fun `deleting an application twice returns not found without a second audit`() {
        val admin = users.findByUsername("admin") ?: error("local admin missing")
        val adminId = requireNotNull(admin.id)
        val adminAuthentication = UsernamePasswordAuthenticationToken(
            adminId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        val saved = applications.save(Application(name = "重复删除申请", qq = "10002"))
        val id = requireNotNull(saved.id)

        mockMvc.perform(delete("/api/admin/applications/{id}", id).with(authentication(adminAuthentication)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deleted").value(true))

        mockMvc.perform(delete("/api/admin/applications/{id}", id).with(authentication(adminAuthentication)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))

        val deletionAudits = audits.findAllByOrderByCreatedAtDesc().count {
            it.resourceType == "APPLICATION" && it.resourceId == id.toString() && it.action == "DELETE"
        }
        assertTrue(deletionAudits == 1)
    }
}
