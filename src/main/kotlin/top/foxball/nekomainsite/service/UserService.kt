package top.foxball.nekomainsite.service

data class CreateUserCommand(
    val username: String,
    val email: String,
    val password: String,
    val role: String,
    val displayName: String?,
    val enabled: Boolean,
)

data class UpdateUserCommand(
    val id: Long,
    val username: String,
    val email: String,
    val password: String?,
    val role: String,
    val displayName: String?,
    val enabled: Boolean,
)

data class UserAdminView(
    val id: Long,
    val username: String,
    val email: String,
    val role: String,
    val authSource: String,
    val displayName: String?,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

interface UserService {
    fun listUsers(): List<UserAdminView>
    fun createUser(command: CreateUserCommand): UserAdminView
    fun createUsers(commands: List<CreateUserCommand>): List<UserAdminView>
    fun getUserById(id: Long): UserAdminView?
    fun getUsersByIds(ids: List<Long>): List<UserAdminView>
    fun updateUser(command: UpdateUserCommand): UserAdminView
    fun updateUsers(commands: List<UpdateUserCommand>): List<UserAdminView>
    fun disableUserById(id: Long): Boolean
    fun disableUsersByIds(ids: List<Long>): Boolean
}
