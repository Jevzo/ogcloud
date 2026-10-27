package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.common.sha256Hex
import io.ogwars.cloud.domain.store.RotatedTokenStore
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import java.time.Clock
import java.time.Duration

/**
 * Remembers every refresh token rotated away. Presenting one again means the client or a thief kept a copy,
 * so the whole authorization is revoked (D-0090).
 */
class RefreshTokenRotation(
    private val delegate: OAuth2AuthorizationService,
    private val rotated: RotatedTokenStore,
    private val clock: Clock,
) : OAuth2AuthorizationService by delegate {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun save(authorization: OAuth2Authorization) {
        val previous = delegate.findById(authorization.id)?.refreshToken?.token
        val expiresAt = previous?.expiresAt
        if (expiresAt != null && previous.tokenValue != authorization.refreshToken?.token?.tokenValue) {
            val remaining = Duration.between(clock.instant(), expiresAt)
            if (remaining.isPositive) rotated.remember(sha256Hex(previous.tokenValue), authorization.id, remaining)
        }
        delegate.save(authorization)
    }

    override fun findByToken(
        token: String,
        tokenType: OAuth2TokenType?,
    ): OAuth2Authorization? {
        val found = delegate.findByToken(token, tokenType)
        if (found == null && (tokenType == null || tokenType == OAuth2TokenType.REFRESH_TOKEN)) revokeIfReused(token)
        return found
    }

    private fun revokeIfReused(token: String) {
        val reused = rotated.authorizationOf(sha256Hex(token))?.let(delegate::findById) ?: return
        log.warn(
            "A rotated refresh token was presented again; revoking authorization {} of {}",
            reused.id,
            reused.registeredClientId,
        )
        delegate.remove(reused)
    }
}
