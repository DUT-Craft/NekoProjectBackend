package `fun`.utf8.nekoprojectbackend.security

import `fun`.utf8.nekoprojectbackend.handlder.TokenExpiredException
import `fun`.utf8.nekoprojectbackend.handlder.TokenInvalidException
import `fun`.utf8.nekoprojectbackend.service.JwtService
import `fun`.utf8.nekoprojectbackend.service.TokenStore
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val tokenStore: TokenStore,
) : OncePerRequestFilter() {

    /**
     * 过滤器内不抛异常：校验失败时把原因写入请求属性，
     * 由后续 JsonAuthEntryPoint 统一输出 401。
     * 这样可避免本过滤器位于 ExceptionTranslationFilter 之前导致异常逃逸到容器。
     */
    override fun doFilterInternal(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = req.getHeader(AUTH_HEADER)
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length), req)
        }
        chain.doFilter(req, resp)
    }

    private fun authenticate(token: String, req: HttpServletRequest) {
        try {
            val claims = jwtService.parse(token)
            val jti = claims.id
            if (jti == null || !tokenStore.isAccessValid(jti)) {
                req.setAttribute(AUTH_ERROR_ATTR, TokenInvalidException("Token 已失效"))
                return
            }
            val principal = toPrincipal(claims)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
        } catch (e: TokenExpiredException) {
            req.setAttribute(AUTH_ERROR_ATTR, e)
        } catch (e: TokenInvalidException) {
            req.setAttribute(AUTH_ERROR_ATTR, e)
        } catch (e: Exception) {
            req.setAttribute(AUTH_ERROR_ATTR, TokenInvalidException())
        }
    }

    private fun toPrincipal(claims: Claims): LoginUser = LoginUser(
        id = claims.subject.toLong(),
        username = claims.get(CLAIM_USERNAME, String::class.java),
        jti = claims.id,
    )

    companion object {
        const val AUTH_ERROR_ATTR = "auth.error"
        private const val AUTH_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val CLAIM_USERNAME = "username"
    }
}
