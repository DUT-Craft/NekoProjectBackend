package `fun`.utf8.nekoprojectbackend.shared

data class Response(
    val status: Int,
    val message: String,
    val data: Any?
)
