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
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import java.util.UUID

/**
 * Signs with the active key and puts the caller and its permissions into access tokens (D-0090, D-0092); Grafana's
 * carry none (D-0185). A user's ID token carries the role and permissions too, Grafana's only the role Grafana takes
 * (D-0153), and the username and email when the profile and email scopes were granted.
 */
class TokenClaims(
    private val activeKeyId: String,
    private val users: WebUserStore,
    private val permissions: UserPermissions,
) : OAuth2TokenCustomizer<JwtEncodingContext> {
    override fun customize(context: JwtEncodingContext) {
        context.jwsHeader.keyId(activeKeyId)
        if (context.tokenType.value == OidcParameterNames.ID_TOKEN) {
            identify(context)
            return
        }
        if (context.tokenType != OAuth2TokenType.ACCESS_TOKEN) return
        context.jwsHeader.type(ACCESS_TOKEN_TYPE)
        val (actor, granted) =
            if (context.authorizationGrantType == AuthorizationGrantType.CLIENT_CREDENTIALS) {
                Actor(ActorType.CLIENT, context.registeredClient.clientId) to
                    context.authorizedScopes.mapNotNull { ApiPermission.fromScope(it) }.toSet()
            } else {
                val user = activeUser(context.getPrincipal<Authentication>()?.name)
                Actor(ActorType.USER, user.id.toString()) to
                    if (forGrafana(context)) emptySet() else permissions.of(user)
            }
        context.claims.claim(ACTOR_CLAIM, actor.toString()).claim(PERMISSIONS_CLAIM, granted.map { it.scope }.sorted())
    }

    private fun identify(context: JwtEncodingContext) {
        val user = activeUser(context.getPrincipal<Authentication>()?.name)
        val scopes = context.authorizedScopes
        if (OidcScopes.PROFILE in scopes) context.claims.claim(USERNAME_CLAIM, user.username)
        if (OidcScopes.EMAIL in scopes) context.claims.claim(EMAIL_CLAIM, user.email)
        val effective = permissions.of(user)
        if (forGrafana(context)) {
            GrafanaRole.of(user, effective)?.let { context.claims.claim(GRAFANA_ROLE_CLAIM, it) }
            return
        }
        context.claims
            .claim(ROLE_CLAIM, user.roleId)
            .claim(PERMISSIONS_CLAIM, effective.map { it.scope }.sorted())
    }

    private fun forGrafana(context: JwtEncodingContext) = context.registeredClient.clientId == FirstPartyClients.GRAFANA

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
        const val ROLE_CLAIM = "role"
        const val GRAFANA_ROLE_CLAIM = "grafana_role"
        const val USERNAME_CLAIM = "preferred_username"
        const val EMAIL_CLAIM = "email"

        /** RFC 9068: the API accepts only access tokens, never ID tokens signed with the same keys. */
        const val ACCESS_TOKEN_TYPE = "at+jwt"
    }
}
