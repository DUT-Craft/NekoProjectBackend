package top.foxball.nekomainsite.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 文件上传/下载配置（neko.file.*）。 */
@ConfigurationProperties(prefix = "neko.file")
data class FileProperties(
    val storagePath: String = "./storage",
    val baseUrl: String = "http://127.0.0.1:8080",
    val image: TypePolicy = TypePolicy(),
) {
    data class TypePolicy(
        val allowedExtensions: List<String> = emptyList(),
        val maxSizeMb: Long = 20L,
    )
}
