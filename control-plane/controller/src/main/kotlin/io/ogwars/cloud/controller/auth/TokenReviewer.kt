package io.ogwars.cloud.controller.auth

import io.ogwars.cloud.controller.config.GrpcAuthProperties
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/** Resolves a projected service-account token to its Kubernetes username, or null when it is not valid for us. */
fun interface TokenReviewer {
    fun username(token: String): String?
}

/** Asks the API server via TokenReview and caches answers briefly; only token hashes are kept in memory. */
@Component
class KubernetesTokenReviewer(
    private val client: KubernetesClient,
    private val properties: GrpcAuthProperties,
    private val clock: Clock,
) : TokenReviewer {
    private val cache = ConcurrentHashMap<String, CachedReview>()

    override fun username(token: String): String? {
        val key = sha256(token)
        val now = clock.instant()
        cache[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.username }
        val username = review(token)
        if (cache.size >= MAX_ENTRIES) cache.values.removeIf { !it.expiresAt.isAfter(now) }
        cache[key] = CachedReview(username, now.plus(properties.cacheTtl))
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
    }
}
