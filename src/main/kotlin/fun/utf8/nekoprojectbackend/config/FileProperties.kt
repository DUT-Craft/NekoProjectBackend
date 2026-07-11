package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 文件上传/下载配置（neko.file.*）。 */
@ConfigurationProperties(prefix = "neko.file")
data class FileProperties(
    val storagePath: String = "./storage",
    val baseUrl: String = "http://localhost:8080",
    val image: TypePolicy = TypePolicy(),
    val document: TypePolicy = TypePolicy(),
    /** 图片是否允许匿名（无 JWT）读取。 */
    val publicReadImage: Boolean = true,
    /** 私有下载签名 token 有效期（秒）。 */
    val downloadTokenTtlSeconds: Long = 300L,
) {
    data class TypePolicy(
        val allowedExtensions: List<String> = emptyList(),
        val maxSizeMb: Long = 20L,
    )
}
