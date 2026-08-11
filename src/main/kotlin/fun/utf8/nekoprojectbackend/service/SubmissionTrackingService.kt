package `fun`.utf8.nekoprojectbackend.service

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

const val SUBMISSION_TRACKING_TOKEN_HEADER = "X-Submission-Tracking-Token"

data class IssuedTrackingToken(
    val token: String,
    val hash: String,
)

data class TrackedSubmission<T>(
    val value: T,
    val trackingToken: String,
)

/** Issues high-entropy anonymous tracking tokens and stores only their SHA-256 digest. */
@Service
class SubmissionTrackingService {
    private val secureRandom = SecureRandom()

    fun issue(): IssuedTrackingToken {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedTrackingToken(token = token, hash = hash(token))
    }

    fun matches(token: String, storedHash: String?): Boolean {
        if (token.isBlank() || storedHash.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            hash(token).toByteArray(StandardCharsets.US_ASCII),
            storedHash.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
