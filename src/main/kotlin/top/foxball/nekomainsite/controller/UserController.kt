package top.foxball.nekomainsite.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.service.CreateUserCommand
import top.foxball.nekomainsite.service.UpdateUserCommand
import top.foxball.nekomainsite.service.UserService
import top.foxball.nekomainsite.shared.ResponseBuilder
import top.foxball.nekomainsite.shared.Response as ApiResponse

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun listUsers(): ResponseEntity<ApiResponse> =
        builder.ok().data(userService.listUsers()).build()

    @PostMapping("/Register")
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<ApiResponse> =
        builder.ok().data(userService.createUser(request.toCommand())).build()

    @PostMapping("/Register/Batch")
    fun createUsers(@Valid @RequestBody requests: List<CreateUserRequest>): ResponseEntity<ApiResponse> {
        validateBatchSize(requests.size)
        return builder.ok().data(userService.createUsers(requests.map(CreateUserRequest::toCommand))).build()
    }

    @GetMapping("/{id}")
    fun getUserInfo(@PathVariable id: Long): ResponseEntity<ApiResponse> =
        userService.getUserById(id)?.let { builder.ok().data(it).build() } ?: builder.notFound().build()

    @GetMapping("/Batch")
    fun getUsersInfo(@RequestParam ids: List<Long>): ResponseEntity<ApiResponse> {
        validateBatchSize(ids.size)
        return builder.ok().data(userService.getUsersByIds(ids)).build()
    }

    @PutMapping("/{id}")
    fun updateUser(
        authentication: Authentication?,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<ApiResponse> {
        preventSelfLockout(authentication.userId(), id, request.role, request.enabled)
        return builder.ok().data(userService.updateUser(request.toCommand(id))).build()
    }

    @PutMapping("/Batch")
    fun updateUsers(authentication: Authentication?, @Valid @RequestBody requests: List<BatchUpdateUserRequest>): ResponseEntity<ApiResponse> {
        validateBatchSize(requests.size)
        val currentUserId = authentication.userId()
        requests.forEach { preventSelfLockout(currentUserId, it.id, it.role, it.enabled) }
        return builder.ok().data(userService.updateUsers(requests.map(BatchUpdateUserRequest::toCommand))).build()
    }

    @DeleteMapping("/{id}")
    fun deleteUser(authentication: Authentication?, @PathVariable id: Long): ResponseEntity<ApiResponse> {
        if (authentication.userId() == id) throw ConflictException("不能停用当前登录账号")
        if (!userService.disableUserById(id)) return builder.notFound().build()
        return builder.ok().data(mapOf("id" to id, "disabled" to true)).build()
    }

    @DeleteMapping("/Batch")
    fun deleteUsers(authentication: Authentication?, @RequestBody ids: List<Long>): ResponseEntity<ApiResponse> {
        validateBatchSize(ids.size)
        if (authentication.userId() in ids) throw ConflictException("不能停用当前登录账号")
        if (!userService.disableUsersByIds(ids)) return builder.notFound().build()
        return builder.ok().data(mapOf("ids" to ids.distinct(), "disabled" to true)).build()
    }

    data class CreateUserRequest(
        @field:NotBlank @field:Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") val username: String,
        @field:NotBlank @field:Email @field:Size(max = 100) val email: String,
        @field:Size(min = 10, max = 128) val password: String,
        @field:Pattern(regexp = "(?i)USER|ADMIN") val role: String = "USER",
        @field:Size(max = 80) val displayName: String? = null,
        val enabled: Boolean = true,
    ) {
        fun toCommand() = CreateUserCommand(username, email, password, role, displayName, enabled)
    }

    data class UpdateUserRequest(
        @field:NotBlank @field:Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") val username: String,
        @field:NotBlank @field:Email @field:Size(max = 100) val email: String,
        @field:Size(min = 10, max = 128) val password: String? = null,
        @field:Pattern(regexp = "(?i)USER|ADMIN") val role: String,
        @field:Size(max = 80) val displayName: String? = null,
        val enabled: Boolean,
    ) {
        fun toCommand(id: Long) = UpdateUserCommand(id, username, email, password, role, displayName, enabled)
    }

    data class BatchUpdateUserRequest(
        val id: Long,
        @field:NotBlank @field:Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") val username: String,
        @field:NotBlank @field:Email @field:Size(max = 100) val email: String,
        @field:Size(min = 10, max = 128) val password: String? = null,
        @field:Pattern(regexp = "(?i)USER|ADMIN") val role: String,
        @field:Size(max = 80) val displayName: String? = null,
        val enabled: Boolean,
    ) {
        fun toCommand() = UpdateUserCommand(id, username, email, password, role, displayName, enabled)
    }

    private fun validateBatchSize(size: Int) {
        if (size !in 1..MAX_BATCH_SIZE) throw ParamErrorException("批量操作每次只允许 1 到 $MAX_BATCH_SIZE 条记录")
    }

    private fun preventSelfLockout(currentUserId: Long?, targetId: Long, targetRole: String, targetEnabled: Boolean) {
        if (currentUserId == targetId && (!targetEnabled || !targetRole.equals("ADMIN", ignoreCase = true))) {
            throw ConflictException("不能停用当前登录账号或移除自己的管理员权限")
        }
    }

    private fun Authentication?.userId(): Long? = this?.principal as? Long

    private companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
