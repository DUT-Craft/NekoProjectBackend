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
 *  - 登录 / 刷新：不要求 JWT（refresh 走 HttpOnly Cookie）
 *  - 公开查询 GET（项目 / 想法列表、详情、计数、子资源）：匿名可访问
 *  - 公开投稿 POST（新项目 / 想法 / 评论 / 加入申请）：匿名提交，后端固定 PENDING 待审
 *  - 项目方自服务（admin/project 下）：凭 controlPassword 鉴权，非 JWT，由 service 层校验
 *  - 文件读取 GET（/api/files 路径）：匿名可读，私有/文档下载由 FileService 二次鉴权
 *  - 其余（管理端 admin/object-items、admin/minds，以及 auth/logout、auth/me，
 *    还有 project 下的写入 PUT/DELETE/batch，以及文件上传/删除 POST/DELETE）均需 JWT
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class, TokenCookieProperties::class, FileProperties::class)
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    private val authEntryPoint: JsonAuthEntryPoint,
    private val accessDeniedHandler: JsonAccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // 登录 / 刷新 / 项目管理注册：不要求 JWT（刷新令牌走 HttpOnly Cookie，注册走一次性邀请码）
                it.requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/register/manager", "/error")
                    .permitAll()
                // 公开查询（GET）：项目 / 想法的列表、详情、计数、动态、评论，匿名可访问
                it.requestMatchers(HttpMethod.GET, "/api/project/**").permitAll()
                // 公开投稿（POST）：新项目 / 想法 / 评论 / 加入申请，匿名提交（后端固定 PENDING 待审）
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/project/object-items",
                    "/api/project/object-items/*/comments",
                    "/api/project/object-items/*/join-applications",
                    "/api/project/minds",
                ).permitAll()
                // 项目方自服务：凭 controlPassword 鉴权（非 JWT），安全层放行，由 service 层校验密码
                it.requestMatchers("/api/admin/project/**").permitAll()
                // 文件读取（GET）：mine 列表需登录（含用户维度数据）；其余匿名可读图片预览，
                // 私有/文档下载由 FileService 二次鉴权。mine 必须声明在 /api/files 通用放行之前（首匹配）。
                it.requestMatchers(HttpMethod.GET, "/api/files/mine").authenticated()
                it.requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 其余接口需 JWT：管理端 /api/admin/object-items、/api/admin/minds、/api/auth/logout、/api/auth/me，
                // 以及 /api/project/** 下的写入（PUT/DELETE/batch）——这些由登录管理员携带 token 调用
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
