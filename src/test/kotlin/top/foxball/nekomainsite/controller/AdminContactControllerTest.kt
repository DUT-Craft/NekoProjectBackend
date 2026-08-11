package top.foxball.nekomainsite.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.ContactRepository
import top.foxball.nekomainsite.repository.UserRepository

@SpringBootTest
@Transactional
class AdminContactControllerTest @Autowired constructor(
    private val controller: AdminContentController,
    private val contacts: ContactRepository,
    private val audits: AuditLogRepository,
    private val users: UserRepository,
) {
    @Test
    fun `contact management uses allowed fields and records lifecycle audit`() {
        val admin = users.findByUsername("admin") ?: error("local admin missing")
        val adminId = requireNotNull(admin.id)
        val authentication = UsernamePasswordAuthenticationToken(
            adminId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        val initial = AdminContentController.ContactRequest(
            slug = "test-contact",
            name = "测试联系人",
            contact = "QQ 10000",
            responsibilities = "测试事项",
            sortOrder = 50,
            published = true,
        )

        controller.createContact(authentication, initial)
        val created = contacts.findBySlug("test-contact") ?: error("contact was not created")
        val createdId = requireNotNull(created.id)
        assertEquals("测试联系人", created.name)
        assertThrows(ConflictException::class.java) { controller.createContact(authentication, initial) }

        controller.updateContact(
            authentication,
            createdId,
            initial.copy(name = "更新后的联系人", contact = "QQ 10001", sortOrder = 51),
        )
        val updated = contacts.findById(createdId).orElseThrow()
        assertEquals("更新后的联系人", updated.name)
        assertEquals("QQ 10001", updated.contact)
        assertEquals(51, updated.sortOrder)

        controller.hideContact(authentication, createdId)
        assertFalse(contacts.findById(createdId).orElseThrow().published)

        val actions = audits.findAllByOrderByCreatedAtDesc()
            .filter { it.resourceType == "CONTACT" && it.resourceId == createdId.toString() }
            .map { it.action }
        assertTrue(actions.containsAll(listOf("CREATE", "UPDATE", "UNPUBLISH")))
    }
}
