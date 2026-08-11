package top.foxball.nekomainsite.service

import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.nekomainsite.authentication.LoginTokenAuthentication
import top.foxball.nekomainsite.config.BlessingSkinProperties
import top.foxball.nekomainsite.entity.jdbc.User
import top.foxball.nekomainsite.handlder.IntegrationUnavailableException
import top.foxball.nekomainsite.handlder.ConflictException
import top.foxball.nekomainsite.handlder.ParamErrorException
import top.foxball.nekomainsite.handlder.UserDisabledException
import top.foxball.nekomainsite.repository.UserRepository
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class BlessingSkinAuthService(
    private val properties: BlessingSkinProperties,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val loginTokenAuthentication: LoginTokenAuthentication,
    private val objectMapper: ObjectMapper,
) {
    private val states = ConcurrentHashMap<String, Long>()
    private val restClient: RestClient = run {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs.coerceAtLeast(1).toLong()))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMs.coerceAtLeast(1).toLong()))
        }
        RestClient.builder().requestFactory(requestFactory).build()
    }

    fun authorizationUrl(): String {
        requireConfigured()
        val now = Instant.now().toEpochMilli()
        states.entries.removeIf { it.value < now }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().toByteArray())
        states[state] = now + STATE_TTL_MILLIS
        return UriComponentsBuilder.fromUriString(properties.authorizationUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", properties.clientId)
            .queryParam("redirect_uri", properties.redirectUri)
            .queryParam("scope", properties.scope)
            .queryParam("state", state)
            .build()
            .encode()
            .toUriString()
    }

    @Transactional
    fun callback(code: String, state: String, userAgent: String): LoginTokenAuthentication.LoginResult {
        requireConfigured()
        val expiresAt = states.remove(state) ?: throw ParamErrorException("登录状态无效或已使用")
        if (expiresAt < Instant.now().toEpochMilli()) throw ParamErrorException("登录状态已过期")
        if (code.isBlank()) throw ParamErrorException("授权码为空")

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("redirect_uri", properties.redirectUri)
            add("code", code)
        }
        val tokenJson = integrationResponse("皮肤站令牌服务暂不可用") {
            restClient.post().uri(properties.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(String::class.java)
        }
        val accessToken = parseJson(tokenJson, "皮肤站令牌响应格式不正确")
            .path("access_token").asString().takeIf { it.isNotBlank() }
            ?: throw IntegrationUnavailableException("皮肤站令牌响应格式不正确")
        val profileJson = integrationResponse("皮肤站用户资料服务暂不可用") {
            restClient.get().uri(properties.userInfoUrl)
                .headers { it.setBearerAuth(accessToken) }
                .retrieve().body(String::class.java)
        }
        val root = parseJson(profileJson, "皮肤站用户资料格式不正确")
        val profile = root.path("data").takeUnless { it.isMissingNode } ?: root
        val externalId = firstText(profile, "id", "uuid", "username") ?: throw ParamErrorException("皮肤站用户资料缺少 ID")
        val displayName = firstText(profile, "name", "nickname", "username") ?: "玩家"
        val email = firstText(profile, "email").orEmpty()
        val user = upsertUser(externalId, displayName, email)
        return loginTokenAuthentication.login(user, userAgent)
    }

    internal fun upsertUser(externalId: String, displayName: String, email: String): User {
        val externalKey = canonicalExternalId(externalId)
        val existing = userRepository.findByExternalUserId(externalKey)
        if (existing != null) {
            if (!existing.enabled) throw UserDisabledException()
            existing.displayName = displayName.take(80)
            existing.email = resolveEmail(email, externalKey, existing.id)
            existing.updatedAt = Instant.now()
            return userRepository.save(existing)
        }
        return userRepository.save(User(
            username = resolveUsername(externalId),
            email = resolveEmail(email, externalKey, null),
            password = passwordEncoder.encode(UUID.randomUUID().toString()) ?: error("password encoder returned null"),
            role = "USER",
            authSource = "BLESSING_SKIN",
            externalUserId = externalKey,
            displayName = displayName.take(80),
        ))
    }

    private fun canonicalExternalId(externalId: String): String {
        val normalized = externalId.trim()
        if (normalized.isBlank()) throw ParamErrorException("皮肤站用户资料缺少 ID")
        return if (normalized.length <= 100) normalized else "sha256:${digest(normalized)}"
    }

    private fun resolveUsername(externalId: String): String {
        val safeId = externalId.trim().replace(Regex("[^A-Za-z0-9_]"), "_")
            .take(40).ifBlank { "user" }
        val hash = digest(externalId.trim())
        val candidates = listOf(
            "blessing_$safeId".take(50),
            "blessing_${safeId.take(29)}_${hash.take(10)}".take(50),
            "blessing_${hash.take(40)}",
        ).distinct()
        return candidates.firstOrNull { !userRepository.existsByUsername(it) }
            ?: throw ConflictException("无法为皮肤站账号生成唯一用户名")
    }

    private fun resolveEmail(preferred: String, externalKey: String, currentUserId: Long?): String {
        val hash = digest(externalKey)
        val candidates = listOf(
            preferred.trim().take(100),
            "blessing-${hash.take(32)}@invalid.local",
            "blessing-$hash@invalid.local",
        ).filter(String::isNotBlank).distinct()
        return candidates.firstOrNull { candidate ->
            val owner = userRepository.findByEmail(candidate)
            owner == null || (currentUserId != null && owner.id == currentUserId)
        } ?: throw ConflictException("无法为皮肤站账号生成唯一邮箱")
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun integrationResponse(message: String, request: () -> String?): String = try {
        request() ?: throw IntegrationUnavailableException(message)
    } catch (ex: IntegrationUnavailableException) {
        throw ex
    } catch (ex: RestClientException) {
        throw IntegrationUnavailableException(message)
    }

    private fun parseJson(value: String, message: String): JsonNode = try {
        objectMapper.readTree(value)
    } catch (ex: RuntimeException) {
        throw IntegrationUnavailableException(message)
    }

    private fun firstText(node: JsonNode, vararg names: String): String? = names.asSequence()
        .map { node.path(it).asString() }
        .firstOrNull { it.isNotBlank() }

    private fun requireConfigured() {
        if (!properties.enabled || !properties.hasValidConfiguration()) {
            throw IntegrationUnavailableException()
        }
    }

    private companion object {
        const val STATE_TTL_MILLIS = 300_000L
    }
}
