package `fun`.utf8.nekoprojectbackend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 内容审核配置（neko.moderation.*）。
 *
 * 控制评论 / 想法等 UGC 新建后的初始状态：
 *  - enabled=true（默认）：开启审核，新建内容置 PENDING，待管理员审核通过后才公开展示（原行为）；
 *  - enabled=false：临时关闭审核，新建内容直接置 APPROVED 立即公开展示。
 *
 * 用环境变量 MODERATION_ENABLED 切换（见 application.yaml / .env.example）。
 */
@ConfigurationProperties(prefix = "neko.moderation")
data class ModerationProperties(
    val enabled: Boolean = true,
)
