package top.foxball.nekomainsite.config

import org.springframework.context.annotation.Profile
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import top.foxball.nekomainsite.entity.jdbc.Activity
import top.foxball.nekomainsite.entity.jdbc.ActivityKind
import top.foxball.nekomainsite.entity.jdbc.ActivityStatus
import top.foxball.nekomainsite.entity.jdbc.Announcement
import top.foxball.nekomainsite.entity.jdbc.AnnouncementStatus
import top.foxball.nekomainsite.entity.jdbc.Contact
import top.foxball.nekomainsite.entity.jdbc.HistoryItem
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerCategory
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.entity.jdbc.WikiSection
import top.foxball.nekomainsite.repository.ActivityRepository
import top.foxball.nekomainsite.repository.AnnouncementRepository
import top.foxball.nekomainsite.repository.ContactRepository
import top.foxball.nekomainsite.repository.HistoryItemRepository
import top.foxball.nekomainsite.repository.ServerRepository
import top.foxball.nekomainsite.repository.UserRepository
import top.foxball.nekomainsite.repository.WikiSectionRepository
import java.time.Instant

@Component
@Profile("local")
class LocalSeedConfig(
    private val properties: LocalProperties,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
    private val serverRepository: ServerRepository,
    private val activityRepository: ActivityRepository,
    private val announcementRepository: AnnouncementRepository,
    private val contactRepository: ContactRepository,
    private val wikiRepository: WikiSectionRepository,
    private val historyRepository: HistoryItemRepository,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        seedAdmin()
        if (serverRepository.count() > 0) return

        val now = Instant.now()
        serverRepository.saveAll(listOf(
            Server(slug = "event", name = "活动服", gameplay = "每周速通 / 小游戏", category = ServerCategory.ACTIVITY, status = ServerStatus.ONLINE, statusLabel = "活动中", onlineCount = 12, capacity = 30, address = "event.neko-mc.club", version = "1.21.1", pack = "不需要整合包", description = "每周速通挑战、小游戏和临时小组服都从这里进入。", rules = "速通期间禁止提前破坏出生点资源，分组以当晚公告为准。", icon = "event", featured = true, createdAt = now, updatedAt = now),
            Server(slug = "redstone", name = "生电服", gameplay = "长期生电 / 公共工程", category = ServerCategory.PERMANENT, status = ServerStatus.ONLINE, statusLabel = "常驻", onlineCount = 5, capacity = 40, address = "redstone.neko-mc.club", version = "1.20.4", pack = "不需要整合包", description = "长期生电、材料生产线和公共工程建设。", rules = "公共机器先登记再改动，长期材料仓库按区域领取。", icon = "redstone", createdAt = now, updatedAt = now),
            Server(slug = "adventure", name = "冒险组", gameplay = "模组合作 / 剧情探索", category = ServerCategory.ACTIVITY, status = ServerStatus.ONLINE, statusLabel = "小组活动", onlineCount = 3, capacity = 20, address = "adventure.neko-mc.club", version = "1.20.1 Forge", pack = "猫娘社冒险组整合包 v3", description = "冒险组、剧情探索和模组合作玩法。", rules = "进入前必须安装整合包，组队路线由当天队长确认。", icon = "adventure", createdAt = now, updatedAt = now),
            Server(slug = "lobby", name = "大厅服", gameplay = "新人集合 / 传送入口", category = ServerCategory.PERMANENT, status = ServerStatus.ONLINE, statusLabel = "新人入口", onlineCount = 1, capacity = 60, address = "play.neko-mc.club", version = "1.21.1", pack = "不需要整合包", description = "新人集合、公告牌、传送牌和基础教程入口。", rules = "新人先在大厅阅读公告牌，再按传送牌进入目标服务器。", icon = "lobby", createdAt = now, updatedAt = now),
            Server(slug = "build", name = "建筑服", gameplay = "建筑 / 截图 / 慢节奏创作", category = ServerCategory.PERMANENT, status = ServerStatus.AVAILABLE, statusLabel = "可以进入", onlineCount = 0, capacity = 30, address = "build.neko-mc.club", version = "1.21.1", pack = "不需要整合包", description = "建筑、截图、社团展示区和慢节奏创作。", rules = "公共建筑请先登记位置，避免占用主路和公共景观。", icon = "build", createdAt = now, updatedAt = now),
            Server(slug = "resource", name = "资源服", gameplay = "资源 / 采集 / 临时周目", category = ServerCategory.PERMANENT, status = ServerStatus.MAINTENANCE, statusLabel = "维护中", onlineCount = 0, capacity = 30, address = "resource.neko-mc.club", version = "1.20.4", pack = "不需要整合包", description = "资源、养老、采集和临时周目，当前正在换周目。", rules = "维护期间不可进入，开放时间以公告栏维护通知为准。", icon = "resource", createdAt = now, updatedAt = now),
        ))
        activityRepository.saveAll(listOf(
            Activity(slug = "weekly-speedrun", name = "本周速通挑战", kind = ActivityKind.WEEKLY, status = ActivityStatus.ACTIVE, statusLabel = "正在进行", serverSlug = "event", timeText = "周六 19:45 集合，20:00 开始", participation = "直接复制活动服地址；新人先到大厅服集合", description = "可以快速开玩、围观或补位，队长会在开始前确认分组和路线。", icon = "event", priority = 100, createdAt = now, updatedAt = now),
            Activity(slug = "redstone-project", name = "生电公共工程", kind = ActivityKind.LONG_TERM, status = ActivityStatus.ONGOING, statusLabel = "长期进行", serverSlug = "redstone", timeText = "常驻开放", participation = "进入生电服后查看公共工程告示牌", description = "材料生产线、公共机器维护和大型工程协作。", icon = "redstone", priority = 80, createdAt = now, updatedAt = now),
            Activity(slug = "adventure-team", name = "暮色探索小队", kind = ActivityKind.LONG_TERM, status = ActivityStatus.ONGOING, statusLabel = "组队中", serverSlug = "adventure", timeText = "每周五晚", participation = "先从 QQ 群文件下载冒险组整合包", description = "剧情探索、模组合作与小队推进。", icon = "adventure", requiresPack = true, priority = 70, createdAt = now, updatedAt = now),
            Activity(slug = "build-gallery", name = "建筑展示区", kind = ActivityKind.LONG_TERM, status = ActivityStatus.ONGOING, statusLabel = "长期开放", serverSlug = "build", timeText = "随时可以参加", participation = "进入建筑服，先登记建筑位置", description = "慢节奏建筑、截图和社团展示内容整理。", icon = "build", priority = 60, createdAt = now, updatedAt = now),
            Activity(slug = "block-relay", name = "方块接力赛", kind = ActivityKind.LIMITED, status = ActivityStatus.UPCOMING, statusLabel = "即将开始", serverSlug = "event", timeText = "下周日 20:00", participation = "活动群内报名", description = "分组完成采集、合成和搭建任务。", icon = "event", priority = 50, createdAt = now, updatedAt = now),
        ))
        announcementRepository.saveAll(listOf(
            Announcement(slug = "speedrun", title = "本周速通挑战今晚开局", category = "event", categoryLabel = "活动通知", publishedAt = now, summary = "活动服 20:00 开放，19:45 到大厅服集合。", content = "请提前确认版本和客户端，临时补位也可以直接到大厅服。", priority = 100, pinned = true, status = AnnouncementStatus.PUBLISHED, createdAt = now, updatedAt = now),
            Announcement(slug = "resource", title = "资源服更换周目维护", category = "maintenance", categoryLabel = "维护通知", publishedAt = now.minusSeconds(86400), summary = "资源服预计维护到周五晚，期间暂时不可进入。", content = "开放时间以维护完成后的公告为准。", priority = 80, status = AnnouncementStatus.PUBLISHED, createdAt = now, updatedAt = now),
            Announcement(slug = "lobby", title = "大厅服新增传送牌", category = "update", categoryLabel = "服务器更新", publishedAt = now.minusSeconds(172800), summary = "新人可以直接从大厅服查看所有服务器入口。", content = "大厅服的传送牌已经按玩法和状态重新整理。", priority = 60, status = AnnouncementStatus.PUBLISHED, createdAt = now, updatedAt = now),
        ))
        contactRepository.saveAll(listOf(
            Contact(slug = "skin", name = "小樱", contact = "QQ 12345678", responsibilities = "皮肤站 / 注册 / 邀请码", sortOrder = 1),
            Contact(slug = "tech", name = "石英", contact = "QQ 23456789", responsibilities = "开服 / 技术 / 整合包", sortOrder = 2),
            Contact(slug = "event", name = "木牌", contact = "QQ 34567890", responsibilities = "活动 / 规则 / 值班", sortOrder = 3),
        ))
        wikiRepository.saveAll(listOf(
            WikiSection(slug = "quick-start", title = "快速入服", summary = "大厅服 -> 传送牌 -> 目标服务器", groupName = "入服与状态", content = "先复制大厅服地址，进入后按照传送牌选择目标服务器。", icon = "lobby", linkUrl = "/home", sortOrder = 1),
            WikiSection(slug = "launcher", title = "启动器下载", summary = "HMCL / PCL 启动器入口", groupName = "玩法与资源", content = "正式下载链接由管理员在后台补充。", icon = "guide", sortOrder = 2),
            WikiSection(slug = "rules", title = "服务器规则", summary = "公共资源与活动规则", groupName = "规则与通知", content = "公共机器先登记，活动规则以当次公告为准。", icon = "rules", linkUrl = "/announcements", sortOrder = 3),
            WikiSection(slug = "packs", title = "整合包", summary = "冒险组与活动整合包", groupName = "玩法与资源", content = "需要整合包的活动会在活动详情中标明下载方式。", icon = "pack", linkUrl = "/activities", sortOrder = 4),
        ))
        historyRepository.saveAll(listOf(
            HistoryItem(slug = "summer-speedrun", title = "夏季速通夜", meta = "7 月 5 日 · 活动服", imageUrl = "/assets_activity-speedrun-image2.webp", altText = "夏季速通夜活动场景", featured = true, content = "社团成员完成了一次夏季速通活动。", happenedAt = now.minusSeconds(1209600)),
            HistoryItem(slug = "adventure-team", title = "暮色冒险组", meta = "6 月 28 日 · 冒险组", imageUrl = "/assets/bg-neko-portal-soft.webp", altText = "暮色冒险组活动场景", content = "冒险组完成了第一阶段探索。", happenedAt = now.minusSeconds(1814400)),
        ))
    }

    private fun seedAdmin() {
        if (userRepository.findByUsername(properties.admin.username) != null) return
        userRepository.save(User(
            username = properties.admin.username,
            email = properties.admin.email,
            password = passwordEncoder.encode(properties.admin.password) ?: error("password encoder returned null"),
            role = "ADMIN",
            displayName = "猫娘社管理员",
        ))
    }
}
