package io.ogwars.cloud.controller.auth

import io.ogwars.cloud.controller.config.GrpcAuthProperties
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat

/** Resolves a projected service-account token to its Kubernetes username, or null when it is not valid for us. */
fun interface TokenReviewer {
    fun username(token: String): String?
}

/**
 * Asks the API server via TokenReview and caches answers briefly in a bounded LRU; only token hashes are kept in
 * memory. Anything that is not a JWT is refused without asking the API server.
 */
@Component
class KubernetesTokenReviewer(
    private val client: KubernetesClient,
    private val properties: GrpcAuthProperties,
    private val clock: Clock,
) : TokenReviewer {
    private val cache =
        object : LinkedHashMap<String, CachedReview>(MAX_ENTRIES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedReview>) = size > MAX_ENTRIES
        }

    override fun username(token: String): String? {
        if (token.length > MAX_TOKEN_LENGTH || !JWT.matches(token)) return null
        val key = sha256(token)
        val now = clock.instant()
        synchronized(cache) { cache[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.username } }
        val username = review(token)
        synchronized(cache) { cache[key] = CachedReview(username, now.plus(properties.cacheTtl)) }
        return username
    }

    private fun review(token: String): String? {
        val request =
            TokenReviewBuilder()
                .withNewSpec()
                .withToken(token)
                .withAudiences(properties.audience)
                .endSpec()
                .build()
        val status =
            client
                .authentication()
                .v1()
                .tokenReviews()
                .create(request)
                .status ?: return null
        return if (status.authenticated == true &&
            properties.audience in status.audiences.orEmpty()
        ) {
            status.user?.username
        } else {
            null
        }
    }

    private fun sha256(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private data class CachedReview(
        val username: String?,
        val expiresAt: Instant,
    )

    private companion object {
        const val MAX_ENTRIES = 1000
        const val LOAD_FACTOR = 0.75f
        const val MAX_TOKEN_LENGTH = 8192
        val JWT = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
    }
}
