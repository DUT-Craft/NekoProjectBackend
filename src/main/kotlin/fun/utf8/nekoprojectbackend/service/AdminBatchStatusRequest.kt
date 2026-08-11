package `fun`.utf8.nekoprojectbackend.service

import jakarta.validation.constraints.Size

/** 管理端批量修改状态的请求：目标 ID 列表 + 目标状态（泛型）。 */
data class AdminBatchStatusRequest<T>(
    @field:Size(min = 1, max = 100, message = "批量操作数量必须为 1 到 100 条")
    val ids: List<Int> = emptyList(),
    val status: T,
)
