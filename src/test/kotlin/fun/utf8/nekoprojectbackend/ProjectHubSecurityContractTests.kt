package `fun`.utf8.nekoprojectbackend

import `fun`.utf8.nekoprojectbackend.controller.PROJECT_CONTROL_PASSWORD_HEADER
import `fun`.utf8.nekoprojectbackend.datasource.jdbc.*
import `fun`.utf8.nekoprojectbackend.handlder.ParamErrorException
import `fun`.utf8.nekoprojectbackend.security.LoginUser
import `fun`.utf8.nekoprojectbackend.service.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectHubSecurityContractTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectItemService: ObjectItemService,
    private val objectItemRepository: ObjectItemRepository,
    private val joinApplicationService: JoinApplicationService,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val mindService: MindService,
    private val commentRepository: ObjectItemCommentRepository,
    private val updateRepository: ObjectItemUpdateRepository,
    private val fileRecordRepository: FileRecordRepository,
    private val passwordEncoder: PasswordEncoder,
    private val fileService: FileService,
    private val storageService: StorageService,
) {

    @Test
    fun `anonymous detail and list endpoints do not expose review records`() {
        val pendingProject = objectItemService.save(projectRequest("Pending project"))
        val pendingMind = mindService.save(
            MindSaveRequest(title = "Pending idea", content = "Private until approved"),
        )

        mockMvc.perform(get("/api/project/object-items/{id}", pendingProject.id))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/project/object-items").param("status", "PENDING"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))

        mockMvc.perform(get("/api/project/minds/{id}", pendingMind.id))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/project/minds").param("status", "PENDING"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    fun `public project responses do not expose control password presence`() {
        val project = publishProject("Public response contract", "public-secret")

        mockMvc.perform(get("/api/project/object-items/{id}", project.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasControlPassword").doesNotExist())

        mockMvc.perform(
            get("/api/project/object-items")
                .param("ids", project.id.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].hasControlPassword").doesNotExist())

        mockMvc.perform(get("/api/project/object-items/status/{status}", ObjectItemStatus.RECRUITING))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].hasControlPassword").doesNotExist())
    }

    @Test
    fun `invalid public request bodies use the unified bad request response`() {
        mockMvc.perform(
            post("/api/project/object-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"","type":"BUILD"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `anonymous project submissions require a control password`() {
        mockMvc.perform(
            post("/api/project/object-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Project without password","type":"BUILD","leader":"Owner"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))

        mockMvc.perform(
            post("/api/project/object-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Project with short password","type":"BUILD","leader":"Owner","controlPassword":"12345"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `admin dynamic edits cannot change status outside the review endpoint`() {
        val admin = LoginUser(1, "admin", Role.SUPER_ADMIN, "admin-jti")
        val project = publishProject("Dynamic status contract")

        mockMvc.perform(
            post("/api/admin/object-items/{id}/updates", project.id)
                .with(user(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"title":"Admin update","content":"Published content","status":"REJECTED"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("APPROVED"))

        val updateId = updateRepository.findByObjectItemId(project.id!!).single().id!!
        mockMvc.perform(
            put("/api/admin/object-items/{id}/updates/{updateId}", project.id, updateId)
                .with(user(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Edited","status":"REJECTED"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/object-items/{id}/updates/{updateId}/status",
                project.id,
                updateId,
            )
                .with(user(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"PENDING"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))

        assertEquals(
            ObjectItemUpdateStatus.APPROVED,
            updateRepository.findById(updateId).orElseThrow().status,
        )
    }

    @Test
    fun `processed join applications cannot be handled twice`() {
        val admin = LoginUser(1, "admin", Role.SUPER_ADMIN, "admin-jti")
        val project = publishProject("Join conflict contract")
        val application = joinApplicationService.create(
            project.id!!,
            JoinApplicationSaveRequest(
                nickName = "Visitor",
                mcId = "visitor",
                contact = "contact",
                reason = "I can help",
                skill = "Builder",
            ),
        )

        val endpoint = "/api/admin/object-items/${project.id}/join-applications/${application.id!!}/accept"
        mockMvc.perform(post(endpoint).with(user(admin)))
            .andExpect(status().isOk)

        mockMvc.perform(post(endpoint).with(user(admin)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `admin detail endpoints can read records hidden from public routes`() {
        val pendingProject = objectItemService.save(projectRequest("Admin project"))
        val pendingMind = mindService.save(
            MindSaveRequest(title = "Admin idea", content = "Needs review"),
        )
        val admin = LoginUser(1, "admin", Role.SUPER_ADMIN, "test-jti")

        mockMvc.perform(
            get("/api/admin/object-items/{id}", pendingProject.id).with(user(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(pendingProject.id))

        mockMvc.perform(
            get("/api/admin/minds/{id}", pendingMind.id).with(user(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(pendingMind.id))
    }

    @Test
    fun `admin detail endpoints preserve authentication and ownership failures`() {
        val project = publishProject("Ownership protected detail")
        val owner = LoginUser(101, "project-owner", Role.PROJECT_MANAGER, "owner-jti")
        val otherManager = LoginUser(102, "other-manager", Role.PROJECT_MANAGER, "other-jti")
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = owner.id
        }.also(objectItemRepository::save)

        mockMvc.perform(get("/api/admin/object-items/{id}", project.id))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            get("/api/admin/object-items/{id}", project.id).with(user(otherManager)),
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            get("/api/admin/object-items/{id}", project.id).with(user(owner)),
        ).andExpect(status().isOk)
    }

    @Test
    fun `project managers cannot maintain or moderate another managers project`() {
        val projectOwner = LoginUser(111, "first-project-owner", Role.PROJECT_MANAGER, "first-owner-jti")
        val otherManager = LoginUser(112, "second-project-owner", Role.PROJECT_MANAGER, "second-owner-jti")
        val project = publishProject("Cross project boundary")
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = projectOwner.id
        }.also(objectItemRepository::save)
        val comment = commentRepository.save(comment(project.id, "needs moderation", ObjectItemCommentStatus.PENDING))
        val update = updateRepository.save(update(project.id, "needs moderation", ObjectItemUpdateStatus.PENDING))

        mockMvc.perform(
            post("/api/admin/object-items/{id}/updates", project.id)
                .with(user(otherManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"forbidden","content":"forbidden"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/project/object-items/{id}", project.id)
                .with(user(otherManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"progress":50}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/object-items/{id}/comments/{commentId}/status",
                project.id,
                comment.id,
            )
                .with(user(otherManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"APPROVED"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/object-items/{id}/updates/{updateId}/status",
                project.id,
                update.id,
            )
                .with(user(otherManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"APPROVED"}"""),
        ).andExpect(status().isForbidden)

        assertEquals(ObjectItemCommentStatus.PENDING, commentRepository.findById(comment.id!!).orElseThrow().status)
        assertEquals(ObjectItemUpdateStatus.PENDING, updateRepository.findById(update.id!!).orElseThrow().status)
    }

    @Test
    fun `super admin can manage and moderate any project`() {
        val projectOwner = LoginUser(121, "managed-owner", Role.PROJECT_MANAGER, "managed-owner-jti")
        val admin = LoginUser(1, "admin", Role.SUPER_ADMIN, "admin-jti")
        val project = publishProject("Super admin boundary")
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = projectOwner.id
        }.also(objectItemRepository::save)
        val comment = commentRepository.save(comment(project.id, "review me", ObjectItemCommentStatus.PENDING))

        mockMvc.perform(
            get("/api/admin/object-items/{id}", project.id).with(user(admin)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/object-items/{id}/comments/{commentId}/status",
                project.id,
                comment.id,
            )
                .with(user(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"APPROVED"}"""),
        ).andExpect(status().isOk)

        assertEquals(ObjectItemCommentStatus.APPROVED, commentRepository.findById(comment.id!!).orElseThrow().status)
    }

    @Test
    fun `idea management endpoints remain super admin only`() {
        val manager = LoginUser(131, "idea-manager", Role.PROJECT_MANAGER, "idea-manager-jti")
        val idea = mindService.save(MindSaveRequest(title = "Restricted idea", content = "Admin only"))

        mockMvc.perform(get("/api/admin/minds").with(user(manager)))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/admin/minds/{id}", idea.id).with(user(manager)))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/admin/minds/batch/status")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${idea.id}],"status":"APPROVED"}"""),
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/project/minds/{id}", idea.id)
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"forbidden"}"""),
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            delete("/api/project/minds/{id}", idea.id).with(user(manager)),
        ).andExpect(status().isForbidden)

        assertEquals("Restricted idea", mindService.findById(idea.id!!).title)
        assertEquals(MindStatus.PENDING, mindService.findById(idea.id).status)
    }

    @Test
    fun `public child resources always return approved rows only`() {
        val project = publishProject("Public child resources")
        commentRepository.save(comment(project.id!!, "pending", ObjectItemCommentStatus.PENDING))
        commentRepository.save(comment(project.id, "approved", ObjectItemCommentStatus.APPROVED))
        updateRepository.save(update(project.id, "pending", ObjectItemUpdateStatus.PENDING))
        updateRepository.save(update(project.id, "approved", ObjectItemUpdateStatus.APPROVED))

        mockMvc.perform(
            get("/api/project/object-items/{id}/comments", project.id).param("status", "PENDING"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("APPROVED"))

        mockMvc.perform(
            get("/api/project/object-items/{id}/updates", project.id).param("status", "REJECTED"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("APPROVED"))
    }

    @Test
    fun `hidden projects reject anonymous comments and join applications`() {
        val project = objectItemService.save(projectRequest("Hidden target"))

        mockMvc.perform(
            post("/api/project/object-items/{id}/comments", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickName":"visitor","content":"hello"}"""),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            post("/api/project/object-items/{id}/join-applications", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"nickName":"visitor","mcId":"visitor","contact":"qq","reason":"help"}""",
                ),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `control passwords are hashed and owner reads require the dedicated header`() {
        val rawPassword = "owner-secret-123"
        val project = objectItemService.save(projectRequest("Password project", rawPassword))
        val storedPassword = objectItemRepository.findById(project.id!!).orElseThrow().controlPassword.orEmpty()

        assertNotEquals(rawPassword, storedPassword)
        assertTrue(passwordEncoder.matches(rawPassword, storedPassword))

        mockMvc.perform(
            get("/api/admin/project/object-items/{id}/updates", project.id)
                .param("controlPassword", rawPassword),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/admin/project/object-items/{id}/updates", project.id)
                .header(PROJECT_CONTROL_PASSWORD_HEADER, rawPassword),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/admin/project/object-items/{id}/updates", project.id)
                .header(PROJECT_CONTROL_PASSWORD_HEADER, "中".repeat(25)),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `project owner cannot change review or deletion status through profile update`() {
        val rawPassword = "owner-status-secret"
        val project = objectItemService.save(projectRequest("Owner status project", rawPassword))

        mockMvc.perform(
            put("/api/admin/project/object-items/{id}", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"controlPassword":"$rawPassword","status":"APPROVED"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/admin/project/object-items/{id}", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"controlPassword":"$rawPassword","status":"DELETED"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/admin/project/object-items/{id}", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"controlPassword":"$rawPassword","status":"PREPARING"}"""),
        ).andExpect(status().isForbidden)

        assertTrue(
            objectItemRepository.findById(project.id!!).orElseThrow().status == ObjectItemStatus.PENDING,
        )

        objectItemService.update(project.id, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))
        mockMvc.perform(
            put("/api/admin/project/object-items/{id}", project.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"controlPassword":"$rawPassword","status":"PAUSED"}"""),
        ).andExpect(status().isOk)
        assertEquals(ObjectItemStatus.PAUSED, objectItemRepository.findById(project.id).orElseThrow().status)
    }

    @Test
    fun `tracking token is accepted only from the dedicated header`() {
        val tracked = mindService.saveTracked(
            MindSaveRequest(title = "Tracked idea", content = "Private status"),
        )

        mockMvc.perform(
            get("/api/project/minds/{id}/status", tracked.value.id)
                .param("trackingToken", tracked.trackingToken),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/project/minds/{id}/status", tracked.value.id)
                .header(SUBMISSION_TRACKING_TOKEN_HEADER, tracked.trackingToken),
        ).andExpect(status().isOk)
    }

    @Test
    fun `cors preflight allows project control and tracking headers`() {
        mockMvc.perform(
            options("/api/project/minds/1/status")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "x-project-control-password,x-submission-tracking-token",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect { result ->
                val allowedHeaders = result.response
                    .getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                    .orEmpty()
                assertTrue(allowedHeaders.contains("x-project-control-password", ignoreCase = true))
                assertTrue(allowedHeaders.contains("x-submission-tracking-token", ignoreCase = true))
            }
    }

    @Test
    fun `refresh cookie endpoint rejects cross site sources`() {
        mockMvc.perform(
            post("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header("Sec-Fetch-Site", "cross-site"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("禁止访问"))
    }

    @Test
    fun `project managers cannot use global query or batch creation endpoints`() {
        val manager = LoginUser(2, "manager", Role.PROJECT_MANAGER, "manager-jti")

        mockMvc.perform(
            post("/api/project/object-items/query")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/project/minds/query")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/project/object-items/batch")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"items":[]}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/project/minds/batch")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"items":[]}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `project managers cannot change review status through jwt project endpoints`() {
        val manager = LoginUser(2, "manager", Role.PROJECT_MANAGER, "manager-jti")
        val rawPassword = "manager-project-secret"
        val project = objectItemService.save(projectRequest("Manager-owned project", rawPassword))
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = manager.id
        }.also(objectItemRepository::save)

        mockMvc.perform(
            put("/api/project/object-items/{id}", project.id)
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"status":"DELETED","controlPassword":"attacker-password"}""",
                ),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/admin/object-items/batch/status")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${project.id}],"status":"REJECTED"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/project/object-items/{id}", project.id)
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"status":"PREPARING","controlPassword":"attacker-password"}""",
                ),
        ).andExpect(status().isForbidden)

        objectItemService.update(project.id, ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING))
        mockMvc.perform(
            put("/api/project/object-items/{id}", project.id)
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"status":"PAUSED","controlPassword":"attacker-password"}""",
                ),
        ).andExpect(status().isOk)

        val stored = objectItemRepository.findById(project.id).orElseThrow()
        assertEquals(ObjectItemStatus.PAUSED, stored.status)
        assertTrue(passwordEncoder.matches(rawPassword, stored.controlPassword.orEmpty()))
    }

    @Test
    fun `project managers cannot soft delete projects through the legacy jwt batch endpoint`() {
        val manager = LoginUser(2, "manager", Role.PROJECT_MANAGER, "manager-jti")
        val project = publishProject("Manager delete boundary")
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = manager.id
        }.also(objectItemRepository::save)

        mockMvc.perform(
            delete("/api/project/object-items/batch")
                .with(user(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${project.id}]}"""),
        ).andExpect(status().isForbidden)

        assertEquals(ObjectItemStatus.RECRUITING, objectItemRepository.findById(project.id).orElseThrow().status)
    }

    @Test
    fun `banned accounts cannot receive projects or appear in the assignable owner list`() {
        val banned = userRepository.save(
            User(
                username = "banned-owner",
                password = checkNotNull(passwordEncoder.encode("password")),
                email = "banned-owner@example.test",
                status = Status.BANNED,
                role = Role.PROJECT_MANAGER,
            ),
        )
        val project = objectItemService.save(projectRequest("Banned owner boundary"))

        assertFailsWith<ParamErrorException> {
            objectItemService.assignOwner(project.id!!, banned.id)
        }
        assertFalse(userService.findAssignableOwners().any { it.id == banned.id })
        assertEquals(null, objectItemRepository.findById(project.id!!).orElseThrow().ownerId)
    }

    @Test
    fun `admin maintenance endpoints return not found for a missing project`() {
        val admin = LoginUser(1, "admin", Role.SUPER_ADMIN, "test-jti")

        mockMvc.perform(
            get("/api/admin/object-items/{id}/updates", 999999).with(user(admin)),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `svg downloads stay attachments even when inline preview is requested`() {
        val svg = """<svg xmlns=\"http://www.w3.org/2000/svg\"><text>safe</text></svg>""".toByteArray()
        val storedName = storageService.store(
            MockMultipartFile("file", "封面.svg", "image/svg+xml", svg),
            "svg",
        )
        val legacyRecord = fileRecordRepository.save(
            FileRecord().apply {
                this.storedName = storedName
                this.originalName = "封面.svg"
                this.mimeType = "image/svg+xml"
                this.size = svg.size.toLong()
                this.category = FileCategory.IMAGE
                this.extension = "svg"
                this.publicRead = true
                this.createTime = LocalDateTime.now()
            },
        )

        try {
            mockMvc.perform(
                get("/api/files/{path}", storedName).param("inline", "true"),
            )
                .andExpect(status().isOk)
                .andExpect { result ->
                    val disposition = result.response
                        .getHeader(HttpHeaders.CONTENT_DISPOSITION)
                        .orEmpty()
                    assertTrue(disposition.startsWith("attachment;"))
                    assertTrue(
                        result.response.getHeader("X-Content-Type-Options").equals("nosniff", ignoreCase = true),
                    )
                }
        } finally {
            storageService.delete(storedName)
            legacyRecord.id?.let(fileRecordRepository::deleteById)
        }
    }

    @Test
    fun `private project files follow the current project owner after reassignment`() {
        val firstOwner = LoginUser(21, "first-owner", Role.PROJECT_MANAGER, "first-owner-jti")
        val nextOwner = LoginUser(22, "next-owner", Role.PROJECT_MANAGER, "next-owner-jti")
        val project = publishProject("Private project files")
        objectItemRepository.findById(project.id!!).orElseThrow().apply {
            ownerId = firstOwner.id
        }.also(objectItemRepository::save)
        val uploaded = fileService.upload(
            MockMultipartFile("file", "notes.txt", "text/plain", "private notes".toByteArray()),
            FileCategory.DOCUMENT,
            firstOwner,
            project.id,
        )

        try {
            mockMvc.perform(get("/api/files/{path}", uploaded.storedName))
                .andExpect(status().isUnauthorized)
            mockMvc.perform(get("/api/files/{path}", uploaded.storedName).with(user(nextOwner)))
                .andExpect(status().isForbidden)
            mockMvc.perform(get("/api/files/{path}", uploaded.storedName).with(user(firstOwner)))
                .andExpect(status().isOk)

            objectItemRepository.findById(project.id).orElseThrow().apply {
                ownerId = nextOwner.id
            }.also(objectItemRepository::save)

            mockMvc.perform(get("/api/files/{path}", uploaded.storedName).with(user(firstOwner)))
                .andExpect(status().isForbidden)
            mockMvc.perform(get("/api/files/{path}", uploaded.storedName).with(user(nextOwner)))
                .andExpect(status().isOk)
            mockMvc.perform(delete("/api/files/{path}", uploaded.storedName).with(user(firstOwner)))
                .andExpect(status().isForbidden)
            mockMvc.perform(delete("/api/files/{path}", uploaded.storedName).with(user(nextOwner)))
                .andExpect(status().isOk)

            assertFalse(storageService.exists(uploaded.storedName))
            assertEquals(null, fileRecordRepository.findByStoredName(uploaded.storedName))
        } finally {
            storageService.delete(uploaded.storedName)
            uploaded.id?.let { id ->
                if (fileRecordRepository.existsById(id)) fileRecordRepository.deleteById(id)
            }
        }
    }

    @Test
    fun `malformed json is reported as a bad request`() {
        mockMvc.perform(
            post("/api/project/minds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    private fun projectRequest(title: String, controlPassword: String? = null) = ObjectItemSaveRequest(
        title = title,
        type = "BUILD",
        leader = "Owner",
        needMembers = listOf(NeedMemberItemRequest(skill = "Builder", number = 2)),
        controlPassword = controlPassword,
    )

    private fun publishProject(title: String, controlPassword: String? = null) =
        objectItemService.save(projectRequest(title, controlPassword)).let { saved ->
        objectItemService.update(
            saved.id!!,
            ObjectItemUpdateRequest(status = ObjectItemStatus.RECRUITING),
        )
    }

    private fun comment(projectId: Int, content: String, status: ObjectItemCommentStatus) =
        ObjectItemComment().apply {
            objectItemId = projectId
            nickName = "visitor"
            this.content = content
            this.status = status
        }

    private fun update(projectId: Int, title: String, status: ObjectItemUpdateStatus) =
        ObjectItemUpdate().apply {
            objectItemId = projectId
            this.title = title
            content = title
            this.status = status
        }
}
