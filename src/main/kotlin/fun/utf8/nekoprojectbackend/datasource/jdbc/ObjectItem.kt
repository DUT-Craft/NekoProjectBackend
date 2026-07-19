package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import jakarta.persistence.*

/** 项目条目实体：一个可招募成员的项目/工程。 */
@Entity
@Table(
    name = "object_item",
    indexes = [
        Index(name = "idx_object_item_status", columnList = "status"),
        Index(name = "idx_object_item_leader_mc_id", columnList = "leader_mc_id"),
    ],
)
class ObjectItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null

    @Column(name = "title", nullable = false, length = 128)
    var title: String? = null

    @Column(name = "introduction", length = 255)
    var introduction: String? = null

    @Lob
    @Column(name = "description")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ObjectItemStatus? = ObjectItemStatus.PENDING

    @Column(name = "leader", length = 64)
    var leader: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "object_item_need_members",
        joinColumns = [JoinColumn(name = "object_item_id", referencedColumnName = "id")],
    )
    var needMembers: MutableList<NeedMemberItem>? = null

    /**
     * 项目标签：多对多关联全局 [Tag] 字典。使用 Set 表达项目内标签不重复；
     * 关联表 object_item_tag 以 (object_item_id, tag_id) 复合主键作为数据库去重最后防线。
     * 删除项目不级联删除全局 Tag（无 CascadeType.REMOVE）。
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "object_item_tag",
        joinColumns = [JoinColumn(name = "object_item_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    var tags: MutableSet<Tag> = linkedSetOf()

    @Column(name = "leader_mc_id", length = 64)
    var leaderMcId: String? = null

    @Column(name = "contact_information", length = 255)
    var contactInformation: String? = null

    /** 项目封面/介绍配图地址（由文件上传接口返回的 url）。 */
    @Column(name = "cover_image_url", length = 512)
    var coverImageUrl: String? = null

    @Column(name = "control_password", length = 255)
    var controlPassword: String? = null

    /** 归属项目管理的用户 ID（users.id）；为空表示未分配，仅总管理可维护。 */
    @Column(name = "owner_id")
    var ownerId: Long? = null
}

/** 招募需求项：岗位技能、人数、说明（嵌入项目条目集合表）。 */
@Embeddable
class NeedMemberItem {
    @Column(name = "skill", length = 64)
    var skill: String? = null

    @Column(name = "number")
    var number: Long? = null

    @Column(name = "context", length = 255)
    var context: String? = null
}

/** 项目状态：待审核→通过/拒绝，以及招募中/进行中/暂停等生命周期。 */
enum class ObjectItemStatus {
    PENDING,

    APPROVED,

    REJECTED,

    DELETED,

    PREPARING,

    RECRUITING,

    IN_PROGRESS,

    PAUSED,
}
