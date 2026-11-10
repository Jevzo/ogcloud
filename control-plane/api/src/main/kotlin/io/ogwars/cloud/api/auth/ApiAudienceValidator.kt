package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Takes only tokens issued to the API's own apps or to machine clients (D-0185); Grafana's, which it may forward to
 * data sources, open nothing.
 */
class ApiAudienceValidator : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val machine =
            token
                .getClaimAsString(TokenClaims.ACTOR_CLAIM)
                ?.let { runCatching { Actor.parse(it) }.getOrNull() }
                ?.type == ActorType.CLIENT
        val app = token.audience.orEmpty().any { it in FirstPartyClients.APPS }
        return if (machine || app) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(INVALID)
        }
    }

    private companion object {
        val INVALID = OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token was not issued for the OgCloud API", null)
    }
}
