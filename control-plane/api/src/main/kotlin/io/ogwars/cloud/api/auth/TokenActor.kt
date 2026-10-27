package io.ogwars.cloud.api.auth

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.domain.audit.Actor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/** The caller as its access token names it (D-0090). */
@Component
class TokenActor : CurrentActor {
    // Only explicitly anonymous endpoints, such as the first-owner setup, run without a token.
    override fun current(): Actor {
        val token = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken ?: return ANONYMOUS
        val actor = token.token.getClaimAsString(TokenClaims.ACTOR_CLAIM) ?: error("The access token names no actor")
        return Actor.parse(actor)
    }

    private companion object {
        val ANONYMOUS = Actor.system("anonymous")
    }
}
