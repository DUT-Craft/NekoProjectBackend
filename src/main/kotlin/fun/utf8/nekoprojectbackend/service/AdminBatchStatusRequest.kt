package `fun`.utf8.nekoprojectbackend.service

/** 管理端批量修改状态的请求：目标 ID 列表 + 目标状态（泛型）。 */
data class AdminBatchStatusRequest<T>(
    val ids: List<Int> = emptyList(),
    val status: T,
)
