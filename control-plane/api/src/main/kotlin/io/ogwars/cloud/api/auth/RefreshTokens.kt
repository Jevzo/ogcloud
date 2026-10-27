package io.ogwars.cloud.api.auth

import io.ogwars.cloud.service.SecretGenerator
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import java.time.Clock

/**
 * Refresh tokens for every client with the refresh grant, PKCE apps included; Spring's own generator skips
 * public clients (D-0090). Each token lives [AuthProperties.Tokens.refreshTokenIdle] from its use, never past
 * [AuthProperties.Tokens.maxLoginAge] after the login.
 */
class RefreshTokens(
    private val lifetimes: AuthProperties.Tokens,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) : OAuth2TokenGenerator<OAuth2RefreshToken> {
    override fun generate(context: OAuth2TokenContext): OAuth2RefreshToken? {
        if (context.tokenType != OAuth2TokenType.REFRESH_TOKEN) return null
        val now = clock.instant()
        // The authorization keeps its (used) code, whose issue time is the login time.
        val loginAt =
            context.authorization
                ?.getToken(OAuth2AuthorizationCode::class.java)
                ?.token
                ?.issuedAt ?: now
        val expiresAt = minOf(now.plus(lifetimes.refreshTokenIdle), loginAt.plus(lifetimes.maxLoginAge))
        return OAuth2RefreshToken(secrets.next(), now, expiresAt)
    }
}
