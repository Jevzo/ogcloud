package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import java.util.UUID

/** Signs with the active key and puts the caller and its permissions into access tokens (D-0090, D-0092). */
class TokenClaims(
    private val activeKeyId: String,
    private val users: WebUserStore,
    private val permissions: UserPermissions,
) : OAuth2TokenCustomizer<JwtEncodingContext> {
    override fun customize(context: JwtEncodingContext) {
        context.jwsHeader.keyId(activeKeyId)
        if (context.tokenType != OAuth2TokenType.ACCESS_TOKEN) return
        context.jwsHeader.type(ACCESS_TOKEN_TYPE)
        val (actor, granted) =
            if (context.authorizationGrantType == AuthorizationGrantType.CLIENT_CREDENTIALS) {
                Actor(ActorType.CLIENT, context.registeredClient.clientId) to
                    context.authorizedScopes.mapNotNull { ApiPermission.fromScope(it) }.toSet()
            } else {
                val user = activeUser(context.getPrincipal<Authentication>()?.name)
                Actor(ActorType.USER, user.id.toString()) to permissions.of(user)
            }
        context.claims.claim(ACTOR_CLAIM, actor.toString()).claim(PERMISSIONS_CLAIM, granted.map { it.scope }.sorted())
    }

    // Users sign in with their id as the principal name (WebUserDetails); a disabled user cannot refresh.
    private fun activeUser(principalName: String?): WebUser =
        principalName
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(users::find)
            ?.value
            ?.takeIf(WebUser::enabled)
            ?: throw OAuth2AuthenticationException(
                OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "The user is disabled or gone", null),
            )

    companion object {
        const val ACTOR_CLAIM = "actor"
        const val PERMISSIONS_CLAIM = "permissions"

        /** RFC 9068: the API accepts only access tokens, never ID tokens signed with the same keys. */
        const val ACCESS_TOKEN_TYPE = "at+jwt"
    }
}
