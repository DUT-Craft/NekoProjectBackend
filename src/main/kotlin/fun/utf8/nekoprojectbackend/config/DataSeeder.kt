package `fun`.utf8.nekoprojectbackend.config

import `fun`.utf8.nekoprojectbackend.datasource.jdbc.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.Locale

/**
 * 应用就绪后通过 JPA 写入一批模拟数据（用户 / 想法 / 项目 / 评论 / 动态 / 申请）。
 *
 * - 由 `neko.seed.enabled` 控制开关，默认开启；
 * - 仅在用户表为空时执行，避免重复启动写脏数据；
 * - 整体包裹在单个事务内，失败则整体回滚。
 */
@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val mindRepository: MindRepository,
    private val objectItemRepository: ObjectItemRepository,
    private val tagRepository: TagRepository,
    private val commentRepository: ObjectItemCommentRepository,
    private val updateRepository: ObjectItemUpdateRepository,
    private val joinApplicationRepository: JoinApplicationRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${neko.seed.enabled:true}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        if (!enabled) {
            log.info("⚠ 跳过模拟数据写入（neko.seed.enabled=false）")
            return
        }
        // 管理员账户由 AdminUserSeeder 独立初始化（先于本类执行），
        // 不能再用「用户表非空」判断是否已播种，改为检查首个演示用户是否存在。
        if (userRepository.findByUsername(DEMO_USERS.first().username) != null) {
            log.info("⚠ 模拟数据已存在，跳过写入")
            return
        }

        try {
            transactionTemplate.executeWithoutResult { seedInternal() }
            log.info("✓ 模拟数据写入完成")
        } catch (e: Exception) {
            log.error("✗ 模拟数据写入失败: ${e.message}", e)
        }
    }

    private fun seedInternal() {
        val password = passwordEncoder.encode(DEFAULT_PASSWORD)!!

        val users = DEMO_USERS.map { (username, email, nickname, status, role) ->
            userRepository.save(
                User(
                    username = username,
                    password = password,
                    email = email,
                    nickname = nickname,
                    status = status,
                    role = role,
                )
            )
        }
        val nicknames = users.map { it.nickname }

        // 想法
        DEMO_MINDS.forEach { (title, content, mcId, status) ->
            mindRepository.save(
                Mind().apply {
                    this.title = title
                    this.content = content
                    this.mcId = mcId
                    this.nickName = nicknames.random()
                    this.status = status
                }
            )
        }

        // 全局标签字典（分组节点 + 可选叶子 + 独立标签），供 Cascader 与项目关联
        val tagByName = seedTags()

        // 项目条目（含招募需求与标签）
        val objectItems = DEMO_OBJECTS.mapIndexed { index, def ->
            objectItemRepository.save(
                ObjectItem().apply {
                    title = def.title
                    introduction = def.introduction
                    description = def.description
                    status = def.status
                    leader = def.leader
                    leaderMcId = def.leaderMcId
                    contactInformation = def.contact
                    controlPassword = password
                    ownerId = users[index % users.size].id
                    tags = def.tags.mapNotNull { name -> tagByName[name.trim().lowercase(Locale.ROOT)] }
                        .toCollection(LinkedHashSet())
                    needMembers = def.needMembers.map { (skill, number, ctx) ->
                        NeedMemberItem().apply {
                            this.skill = skill
                            this.number = number.toLong()
                            this.context = ctx
                        }
                    }.toMutableList()
                }
            )
        }

        // 项目评论
        objectItems.take(3).forEach { item ->
            repeat(2) {
                commentRepository.save(
                    ObjectItemComment().apply {
                        objectItemId = item.id
                        nickName = nicknames.random()
                        content = "这个项目看起来很有趣，期待后续进展！"
                        status = ObjectItemCommentStatus.APPROVED
                    }
                )
            }
        }

        // 项目动态
        objectItems.take(3).forEach { item ->
            updateRepository.save(
                ObjectItemUpdate().apply {
                    objectItemId = item.id
                    title = "【进度更新】第一阶段完成"
                    content = "我们已完成核心模块的搭建，下一步进入联调阶段。"
                    status = ObjectItemUpdateStatus.APPROVED
                }
            )
        }

        // 加入申请
        objectItems.take(3).forEach { item ->
            joinApplicationRepository.save(
                JoinApplication().apply {
                    objectItemId = item.id
                    nickName = nicknames.random()
                    mcId = "MC_${(1000..9999).random()}"
                    contact = "qq@example.com"
                    reason = "我对这个项目很感兴趣，希望加入一起建设。"
                    skill = "建筑"
                    status = JoinApplicationStatus.PENDING
                }
            )
        }
    }

    /**
     * 幂等播种全局标签字典：分组节点（selectable=false）+ 可选叶子 + 独立标签。
     * 返回 normalizedName → Tag 映射，供项目关联按名称复用。重复执行不会创建重复 Tag。
     */
    private fun seedTags(): Map<String, Tag> {
        val byName = mutableMapOf<String, Tag>()
        val now = LocalDateTime.now()

        fun norm(name: String) = name.trim().lowercase(Locale.ROOT)

        fun getOrCreate(name: String, parent: Tag?, selectable: Boolean, sortOrder: Int): Tag {
            val key = norm(name)
            byName[key]?.let { return it }
            val tag = Tag().apply {
                this.name = name.trim()
                this.normalizedName = key
                this.parentId = parent?.id
                this.selectable = selectable
                this.sortOrder = sortOrder
                this.createTime = now
                this.updateTime = now
            }
            return tagRepository.save(tag).also { byName[norm(it.name!!)] = it }
        }

        TAG_TREE.forEachIndexed { groupOrder, group ->
            val groupTag = getOrCreate(group.name, parent = null, selectable = false, sortOrder = groupOrder)
            group.children.forEachIndexed { idx, child ->
                getOrCreate(child, parent = groupTag, selectable = true, sortOrder = idx)
            }
        }
        STANDALONE_TAGS.forEachIndexed { idx, name ->
            getOrCreate(name, parent = null, selectable = true, sortOrder = idx)
        }
        return byName
    }

    private companion object {
        const val DEFAULT_PASSWORD = "nekobox123"

        val DEMO_USERS = listOf(
            SeedUser("nyaa", "nyaa@nekobox.local", "Nyaako", Status.ACTIVE, Role.PROJECT_MANAGER),
            SeedUser("shiro", "shiro@nekobox.local", "Shiro", Status.ACTIVE, Role.PROJECT_MANAGER),
            SeedUser("kuro", "kuro@nekobox.local", "KuroNeko", Status.ACTIVE, Role.PROJECT_MANAGER),
            SeedUser("frozen", "frozen@nekobox.local", "Frozen", Status.BANNED, Role.PROJECT_MANAGER),
        )

        val DEMO_MINDS = listOf(
            SeedMind(
                title = "建议增加跨服同步背包",
                content = "希望能把背包数据跨服同步，方便多端游玩。",
                mcId = "Notch_001",
                status = MindStatus.PENDING,
            ),
            SeedMind(
                title = "希望优化凌晨时段的延迟",
                content = "凌晨 2-5 点服务器延迟明显升高，能否排查一下？",
                mcId = "Steve_042",
                status = MindStatus.APPROVED,
            ),
            SeedMind(
                title = "想要一个空岛生存模式",
                content = "新增空岛生存玩法应该会很有趣，期待上线！",
                mcId = "Alex_077",
                status = MindStatus.REJECTED,
            ),
        )

        /** Cascader 预设标签树：分组节点不可选，叶子节点可关联项目。 */
        val TAG_TREE = listOf(
            SeedTagGroup("项目方向", listOf("建筑", "生电", "红石", "RPG", "生存")),
            SeedTagGroup("项目周期", listOf("长期", "短期")),
        )

        /** 不属于任何分组的独立可选标签。 */
        val STANDALONE_TAGS = listOf("硬核", "剧情")

        val DEMO_OBJECTS = listOf(
            SeedObject(
                title = "云端小镇共建计划",
                introduction = "邀请玩家共同搭建一座云端小镇。",
                description = "我们正在搭建一座漂浮于天际的小镇，招募建筑党与红石党共同参与。",
                status = ObjectItemStatus.RECRUITING,
                leader = "Nyaako",
                leaderMcId = "Nyaako_001",
                contact = "qq-group: 100200",
                tags = listOf("建筑", "长期"),
                needMembers = listOf(
                    SeedNeed("建筑", 4, "负责外观设计与施工"),
                    SeedNeed("红石", 1, "负责机关与电路"),
                ),
            ),
            SeedObject(
                title = "硬核生存服试运营",
                introduction = "高难度生存体验，原版机制加强。",
                description = "禁用部分作弊指令、加强怪物 AI，追求原版硬核生存体验。",
                status = ObjectItemStatus.IN_PROGRESS,
                leader = "Shiro",
                leaderMcId = "Shiro_002",
                contact = "discord: shiro#0001",
                tags = listOf("生存", "硬核"),
                needMembers = listOf(
                    SeedNeed("生存", 6, "每日活跃玩家"),
                    SeedNeed("策划", 1, "设计经济与规则"),
                ),
            ),
            SeedObject(
                title = "剧情 RPG 地图制作",
                introduction = "原创剧情 RPG 地图，长期项目。",
                description = "正在制作一张包含主线剧情与分支任务的 RPG 地图，招募编剧与命令方块玩家。",
                status = ObjectItemStatus.PREPARING,
                leader = "KuroNeko",
                leaderMcId = "Kuro_003",
                contact = "email: kuro@nekobox.local",
                tags = listOf("RPG", "剧情", "长期"),
                needMembers = listOf(
                    SeedNeed("编剧", 2, "撰写主线与支线剧情"),
                    SeedNeed("命令方块", 1, "实现机制与机关"),
                ),
            ),
        )
    }

    private data class SeedUser(
        val username: String,
        val email: String,
        val nickname: String,
        val status: Status,
        val role: Role,
    )

    private data class SeedMind(val title: String, val content: String, val mcId: String, val status: MindStatus)
    private data class SeedTagGroup(val name: String, val children: List<String>)
    private data class SeedNeed(val skill: String, val number: Int, val context: String)
    private data class SeedObject(
        val title: String,
        val introduction: String,
        val description: String,
        val status: ObjectItemStatus,
        val leader: String,
        val leaderMcId: String,
        val contact: String,
        val tags: List<String>,
        val needMembers: List<SeedNeed>,
    )
}
