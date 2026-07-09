package `fun`.utf8.nekoprojectbackend.service

data class AdminBatchStatusRequest<T>(
    val ids: List<Int> = emptyList(),
    val status: T,
)
