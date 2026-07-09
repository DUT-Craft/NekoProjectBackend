package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.MindStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class MindSaveRequest(
    val title: String = "",
    val nickName: String? = null,
    val status: MindStatus? = MindStatus.PENDING,
    val content: String? = null,
    val mcId: String? = null,
)

data class MindBatchSaveRequest(
    val items: List<MindSaveRequest> = emptyList(),
)

data class MindUpdateRequest(
    val id: Int? = null,
    val title: String? = null,
    val nickName: String? = null,
    val status: MindStatus? = null,
    val content: String? = null,
    val mcId: String? = null,
)

data class MindBatchUpdateRequest(
    val items: List<MindUpdateRequest> = emptyList(),
)

data class MindBatchDeleteRequest(
    val ids: List<Int> = emptyList(),
)

data class MindQueryRequest(
    val ids: List<Int>? = null,
    val title: String? = null,
    val nickName: String? = null,
    val status: MindStatus? = null,
    val statuses: List<MindStatus>? = null,
    val mcId: String? = null,
)

data class MindResponse(
    val id: Int?,
    val title: String?,
    val nickName: String?,
    val status: MindStatus?,
    val content: String?,
    val mcId: String?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
)

data class MindPageVO(
    val content: List<MindResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

enum class MindSortProperty(val alias: String) {
    ID("id"),
    CREATE_TIME("createTime"),
    UPDATE_TIME("updateTime"),
    ;

    companion object {
        fun from(value: String): MindSortProperty? =
            entries.firstOrNull { it.alias.equals(value, ignoreCase = true) }
    }
}

// SortDirection 定义于 ObjectItemService.kt，此处不再重复声明，避免同包重复定义。

/** 想法业务。当前为桩实现，各方法均以 [TODO] 抛出，待补充。 */
@Service
class MindService {

    fun save(request: MindSaveRequest): MindResponse = TODO("MindService.save 尚未实现")

    fun saveBatch(requests: List<MindSaveRequest>): List<MindResponse> = TODO("MindService.saveBatch 尚未实现")

    fun findById(id: Int): MindResponse = TODO("MindService.findById 尚未实现")

    fun query(request: MindQueryRequest): List<MindResponse> = TODO("MindService.query 尚未实现")

    fun queryPage(request: MindQueryRequest, page: Int, size: Int, sort: String): MindPageVO =
        TODO("MindService.queryPage 尚未实现")

    fun findByStatus(status: MindStatus): List<MindResponse> = TODO("MindService.findByStatus 尚未实现")

    fun findByStatuses(statuses: List<MindStatus>): List<MindResponse> = TODO("MindService.findByStatuses 尚未实现")

    fun countApproved(): Long = TODO("MindService.countApproved 尚未实现")

    fun update(id: Int, request: MindUpdateRequest): MindResponse = TODO("MindService.update 尚未实现")

    fun updateBatch(requests: List<MindUpdateRequest>): List<MindResponse> = TODO("MindService.updateBatch 尚未实现")

    fun delete(id: Int) {
        TODO("MindService.delete 尚未实现")
    }

    fun deleteBatch(ids: List<Int>) {
        TODO("MindService.deleteBatch 尚未实现")
    }
}
