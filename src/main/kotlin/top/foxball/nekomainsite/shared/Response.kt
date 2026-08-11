package top.foxball.nekomainsite.shared

/** 统一响应体：状态码 + 消息 + 数据。 */
data class Response(
    val status: Int,
    val message: String,
    val data: Any?
)
