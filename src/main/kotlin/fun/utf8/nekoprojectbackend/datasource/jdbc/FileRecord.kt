package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

/** 文件记录实体：一次上传落盘后的元数据，供鉴权与业务关联。 */
@Entity
@Table(
    name = "file_record",
    indexes = [
        Index(name = "idx_file_record_stored_name", columnList = "stored_name", unique = true),
        Index(name = "idx_file_record_uploader", columnList = "uploader_id"),
        Index(name = "idx_file_record_object_item", columnList = "object_item_id"),
    ],
)
class FileRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "stored_name", nullable = false, length = 128, unique = true)
    var storedName: String? = null

    @Column(name = "original_name", nullable = false, length = 255)
    var originalName: String? = null

    @Column(name = "mime_type", length = 128)
    var mimeType: String? = null

    @Column(name = "size")
    var size: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    var category: FileCategory? = null

    @Column(name = "extension", length = 16)
    var extension: String? = null

    @Column(name = "uploader_id")
    var uploaderId: Long? = null

    @Column(name = "object_item_id")
    var objectItemId: Int? = null

    @Column(name = "public_read", nullable = false)
    var publicRead: Boolean? = true

    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime? = null

    @PrePersist
    fun prePersist() {
        createTime = createTime ?: LocalDateTime.now()
    }
}

/** 文件分类：图片 / 文档，决定白名单与读取策略。 */
enum class FileCategory {
    IMAGE,

    DOCUMENT,
}
