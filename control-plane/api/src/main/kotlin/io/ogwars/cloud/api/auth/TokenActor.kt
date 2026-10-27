package io.ogwars.cloud.api.auth

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.domain.audit.Actor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/** The caller as its access token names it (D-0090). */
@Component
class TokenActor : CurrentActor {
    override fun get(request: HttpServletRequest): Actor {
        val token = request.userPrincipal as? JwtAuthenticationToken ?: error("API changes require an access token")
        val actor = token.token.getClaimAsString(TokenClaims.ACTOR_CLAIM) ?: error("The access token names no actor")
        return Actor.parse(actor)
    }
}
