package `fun`.utf8.nekoprojectbackend.config

import `fun`.utf8.nekoprojectbackend.security.JsonAccessDeniedHandler
import `fun`.utf8.nekoprojectbackend.security.JsonAuthEntryPoint
import `fun`.utf8.nekoprojectbackend.security.JwtAuthenticationFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security 配置：无状态 JWT。
 *
 * 鉴权分级（authorizeHttpRequests 按声明顺序首匹配）：
 *  - 登录 / 刷新 / 注册 / 邮箱验证码 / 找回密码 / 邮箱验证登录：不要求 JWT（匿名流程）
 *  - 公开查询 GET（项目 / 想法列表、详情、计数、子资源）：匿名可访问
 *  - 公开投稿 POST（新项目 / 想法 / 评论 / 加入申请）：匿名提交，后端固定 PENDING 待审
 *  - 文件读取 GET（/api/files 路径）：匿名可读，私有/文档下载由 FileService 二次鉴权
 *  - 其余（管理端 admin/object-items、admin/minds，auth/logout、auth/me、auth/change-password，
 *    还有 project 下的写入 PUT/DELETE/batch，以及文件上传/删除 POST/DELETE）均需 JWT
 */
@Configuration
@EnableConfigurationProperties(
    JwtProperties::class,
    TokenCookieProperties::class,
    FileProperties::class,
    MailProperties::class,
    ModerationProperties::class,
    NekoSecurityProperties::class,
)
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    private val authEntryPoint: JsonAuthEntryPoint,
    private val accessDeniedHandler: JsonAccessDeniedHandler,
    private val jwtProperties: JwtProperties,
    private val env: org.springframework.core.env.Environment,
) {

    init {
        // 启动即校验 JWT 密钥：prod profile 下禁止使用开发默认密钥 / 空密钥 / 过短密钥。
        // 快速失败优于带着弱密钥上线——弱密钥等于任何人可伪造任意角色 token。
        val isProd = env.activeProfiles.any { it.equals("prod", ignoreCase = true) }
        val secret = jwtProperties.secret
        require(secret.isNotBlank()) { "security.jwt.secret 未配置：请通过 JWT_SECRET 环境变量设置 ≥32 字节随机串" }
        if (isProd) {
            require(secret.length >= 32) {
                "prod 环境下 security.jwt.secret 必须 ≥32 字节（当前 ${secret.length}），请设置 JWT_SECRET 环境变量"
            }
            require(secret != DEV_DEFAULT_SECRET) {
                "prod 环境下禁止使用开发默认 JWT 密钥：请设置 JWT_SECRET 环境变量为独立随机串"
            }
        }
    }

    private companion object {
        // 与 application.yaml 中开发默认值保持一致，用于 prod 启动守卫比对
        const val DEV_DEFAULT_SECRET = "neko-backend-local-dev-secret-2026-change-me"
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // 登录 / 刷新 / 注册 / 邮箱验证码 / 找回密码 / 邮箱验证登录：不要求 JWT
                // （刷新令牌走 HttpOnly Cookie，注册走邮箱验证码，验证码/找回/邮箱登录为匿名流程）
                it.requestMatchers(
                    "/api/auth/login",
                    "/api/auth/login/email",
                    "/api/auth/refresh",
                    "/api/auth/register",
                    "/api/auth/verification-code",
                    "/api/auth/reset-password",
                    "/api/auth/username/recover",
                    "/api/auth/reactivate",
                    "/error",
                ).permitAll()
                // 公开查询（GET）：项目 / 想法的列表、详情、计数、动态、评论，匿名可访问
                it.requestMatchers(HttpMethod.GET, "/api/project/**").permitAll()
                // 文件读取（GET）：mine 列表需登录（含用户维度数据）；其余匿名可读图片预览，
                // 私有/文档下载由 FileService 二次鉴权。mine 必须声明在 /api/files 通用放行之前（首匹配）。
                it.requestMatchers(HttpMethod.GET, "/api/files/mine").authenticated()
                it.requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 其余接口需 JWT：管理端 /api/admin/**（项目维护 / 审核 / 审计 / 用户）、
                // /api/auth/logout、/api/auth/me、/api/user/**（账号生命周期）、/api/auth/sessions，
                // 以及 /api/project/** 下的写入（POST 投稿 / PUT / DELETE / batch）——均由登录用户携带 token 调用
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            // origin 走回显：allowedOriginPatterns 支持带凭证，浏览器会收到具体 origin（而非字面 *）
            allowedOriginPatterns = listOf("*")
            // 方法 / 请求头必须显式列举：allowCredentials=true 时，浏览器不接受通配 *，
            // 否则 preflight 会以 "field content-type is not allowed by Access-Control-Allow-Headers" 拦截
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "X-Requested-With")
            allowCredentials = true
            maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
