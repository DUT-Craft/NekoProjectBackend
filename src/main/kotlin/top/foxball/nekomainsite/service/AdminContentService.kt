package top.foxball.nekomainsite.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus
import top.foxball.nekomainsite.entity.jdbc.AuditLog
import top.foxball.nekomainsite.entity.jdbc.Contact
import top.foxball.nekomainsite.entity.jdbc.FeedbackStatus
import top.foxball.nekomainsite.entity.jdbc.ModerationStatus
import top.foxball.nekomainsite.entity.jdbc.RegistrationStatus
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.repository.ActivityRegistrationRepository
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.ApplicationRepository
import top.foxball.nekomainsite.repository.AuditLogRepository
import top.foxball.nekomainsite.repository.ContactRepository
import top.foxball.nekomainsite.repository.IdeaRepository
import top.foxball.nekomainsite.repository.PrivateFeedbackRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.shared.requirePresent
import java.time.Instant

data class AdminOverviewView(
    val servers: Long,
    val maintenanceServers: Long,
    val pendingApplications: Long,
    val pendingIdeas: Long,
    val pendingRegistrations: Long,
    val openFeedback: Long,
    val publishedAnnouncements: Long,
)

data class AdminContactCommand(
    val slug: String,
    val name: String,
    val contact: String,
    val responsibilities: String,
    val sortOrder: Int,
    val published: Boolean,
)

data class AdminContactView(
    val id: Long,
    val slug: String,
    val name: String,
    val contact: String,
    val responsibilities: String,
    val sortOrder: Int,
    val published: Boolean,
)

data class AdminAuditLogView(
    val id: Long,
    val operatorUserId: Long,
    val resourceType: String,
    val resourceId: String,
    val action: String,
    val createdAt: String,
)

data class AdminServerStatusView(
    val id: Long,
    val status: String,
    val statusLabel: String,
    val onlineCount: Int,
    val capacity: Int,
    val maintenance: Boolean,
    val lastCheckedAt: String?,
    val statusError: String?,
)

data class AdminPublicationView(val id: Long, val published: Boolean)

@Service
@Transactional(readOnly = true)
class AdminContentService(
    private val serverRepository: ServerRepository,
    private val announcementRepository: AnnouncementRepository,
    private val contactRepository: ContactRepository,
    private val applicationRepository: ApplicationRepository,
    private val ideaRepository: IdeaRepository,
    private val registrationRepository: ActivityRegistrationRepository,
    private val feedbackRepository: PrivateFeedbackRepository,
    private val auditLogRepository: AuditLogRepository,
    private val siteContentService: SiteContentService,
    private val serverStatusService: ServerStatusService,
) {
    fun overview() = AdminOverviewView(
        servers = serverRepository.countByPublishedTrue(),
        maintenanceServers = serverRepository.countByPublishedTrueAndMaintenanceTrue(),
        pendingApplications = applicationRepository.countByStatus(ModerationStatus.PENDING),
        pendingIdeas = ideaRepository.countByStatus(ModerationStatus.PENDING),
        pendingRegistrations = registrationRepository.countByStatus(RegistrationStatus.PENDING),
        openFeedback = feedbackRepository.countByStatus(FeedbackStatus.OPEN),
        publishedAnnouncements = announcementRepository.countByStatus(AnnouncementStatus.PUBLISHED),
    )

    fun applications(): List<AdminApplicationView> = siteContentService.adminApplications()
    fun ideas(): List<AdminIdeaView> = siteContentService.adminIdeas()
    fun registrations(): List<AdminRegistrationView> = siteContentService.adminRegistrations()
    fun feedback(): List<AdminFeedbackView> = siteContentService.adminFeedback()

    @Transactional
    fun updateIdea(operatorId: Long?, id: Long, status: ModerationStatus, note: String?): IdeaView {
        val result = siteContentService.updateIdeaStatus(id, status, note)
        audit(operatorId, "IDEA", id.toString(), "MODERATE_${status.name}")
        return result
    }

    @Transactional
    fun updateApplication(operatorId: Long?, id: Long, status: ModerationStatus, note: String?): AdminApplicationView {
        val result = siteContentService.updateApplicationStatus(id, status, note)
        audit(operatorId, "APPLICATION", id.toString(), "MODERATE_${status.name}")
        return result
    }

    @Transactional
    fun updateFeedback(operatorId: Long?, id: Long, status: FeedbackStatus, note: String?): AdminFeedbackView {
        val result = siteContentService.updateFeedbackStatus(id, status, note)
        audit(operatorId, "FEEDBACK", id.toString(), "MODERATE_${status.name}")
        return result
    }

    @Transactional
    fun updateRegistration(operatorId: Long?, id: Long, status: RegistrationStatus, note: String?): AdminRegistrationView {
        val result = siteContentService.updateRegistrationStatus(id, status, note)
        audit(operatorId, "REGISTRATION", id.toString(), "MODERATE_${status.name}")
        return result
    }

    @Transactional
    fun refreshServer(operatorId: Long?, id: Long): AdminServerStatusView {
        serverStatusService.refreshById(id)
        val server = serverRepository.findById(id).orElseThrow { ResourceNotFoundException("服务器不存在") }
        audit(operatorId, "SERVER", id.toString(), "STATUS_REFRESH")
        return serverStatusView(server)
    }

    @Transactional
    fun setServerMaintenance(operatorId: Long?, id: Long, maintenance: Boolean): AdminServerStatusView {
        val server = serverRepository.findById(id).orElseThrow { ResourceNotFoundException("服务器不存在") }
        server.maintenance = maintenance
        server.onlineCount = 0
        server.updatedAt = Instant.now()
        if (maintenance) {
            server.status = ServerStatus.MAINTENANCE
            server.statusLabel = "维护中"
            server.statusError = null
            server.lastCheckedAt = Instant.now()
        } else if (server.status == ServerStatus.MAINTENANCE) {
            server.status = ServerStatus.AVAILABLE
            server.statusLabel = "待检测"
            server.statusError = null
            server.lastCheckedAt = null
        }
        val saved = serverRepository.save(server)
        audit(operatorId, "SERVER", id.toString(), if (maintenance) "MAINTENANCE_START" else "MAINTENANCE_END")
        return serverStatusView(saved)
    }

    fun contacts(): List<AdminContactView> = contactRepository.findAllByOrderBySortOrderAsc().map(::contactView)

    @Transactional
    fun createContact(operatorId: Long?, command: AdminContactCommand): AdminContactView {
        if (contactRepository.findBySlug(command.slug.trim()) != null) throw ConflictException("联系人标识已存在")
        val saved = contactRepository.save(Contact().apply { apply(command) })
        audit(operatorId, "CONTACT", saved.id.toString(), "CREATE")
        return contactView(saved)
    }

    @Transactional
    fun updateContact(operatorId: Long?, id: Long, command: AdminContactCommand): AdminContactView {
        contactRepository.findBySlug(command.slug.trim())
            ?.takeIf { it.id != id }
            ?.let { throw ConflictException("联系人标识已存在") }
        val item = contactRepository.findById(id).orElseThrow { ResourceNotFoundException("联系人不存在") }
        item.apply(command)
        val saved = contactRepository.save(item)
        audit(operatorId, "CONTACT", id.toString(), "UPDATE")
        return contactView(saved)
    }

    @Transactional
    fun hideContact(operatorId: Long?, id: Long): AdminPublicationView {
        val item = contactRepository.findById(id).orElseThrow { ResourceNotFoundException("联系人不存在") }
        item.published = false
        contactRepository.save(item)
        audit(operatorId, "CONTACT", id.toString(), "UNPUBLISH")
        return AdminPublicationView(id, false)
    }

    fun auditLogs(): List<AdminAuditLogView> = auditLogRepository.findTop200ByOrderByCreatedAtDesc().map {
        AdminAuditLogView(
            id = it.id.requirePresent("AuditLog.id"),
            operatorUserId = it.operatorUserId,
            resourceType = it.resourceType,
            resourceId = it.resourceId,
            action = it.action,
            createdAt = it.createdAt.toString(),
        )
    }

    private fun Contact.apply(command: AdminContactCommand) {
        slug = command.slug.trim()
        name = command.name.trim()
        contact = command.contact.trim()
        responsibilities = command.responsibilities.trim()
        sortOrder = command.sortOrder
        published = command.published
    }

    private fun contactView(item: Contact) = AdminContactView(
        id = item.id.requirePresent("Contact.id"),
        slug = item.slug,
        name = item.name,
        contact = item.contact,
        responsibilities = item.responsibilities,
        sortOrder = item.sortOrder,
        published = item.published,
    )

    private fun serverStatusView(server: Server) = AdminServerStatusView(
        id = server.id.requirePresent("Server.id"),
        status = server.status.name,
        statusLabel = server.statusLabel,
        onlineCount = server.onlineCount,
        capacity = server.capacity,
        maintenance = server.maintenance,
        lastCheckedAt = server.lastCheckedAt?.toString(),
        statusError = server.statusError,
    )

    private fun audit(operatorId: Long?, type: String, id: String, action: String) {
        if (operatorId == null) return
        auditLogRepository.save(AuditLog(operatorUserId = operatorId, resourceType = type, resourceId = id, action = action))
    }
}
