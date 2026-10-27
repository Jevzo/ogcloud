package io.ogwars.cloud.api.auth

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Component
import java.net.URI

/** Registers the web, desktop and mobile apps as public PKCE clients on every start (D-0012, D-0090). */
@Component
class FirstPartyClients(
    private val clients: RegisteredClientRepository,
    private val properties: AuthProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        definitions().forEach { client ->
            try {
                clients.save(client)
            } catch (_: DuplicateKeyException) {
                // Another replica inserted it first; saving again updates that row.
                clients.save(client)
            }
        }
    }

    fun definitions(): List<RegisteredClient> =
        listOf(
            app(
                WEB,
                "OgCloud dashboard",
                properties.issuer.resolve(properties.clients.webRedirectPath),
                properties.issuer,
            ),
            app(DESKTOP, "OgCloud desktop", properties.clients.desktopRedirectUri, null),
            app(MOBILE, "OgCloud mobile", properties.clients.mobileRedirectUri, null),
        )

    private fun app(
        clientId: String,
        name: String,
        redirectUri: URI,
        postLogoutRedirectUri: URI?,
    ): RegisteredClient =
        RegisteredClient
            .withId(clientId)
            .clientId(clientId)
            .clientName(name)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri(redirectUri.toString())
            .apply { if (postLogoutRedirectUri != null) postLogoutRedirectUri(postLogoutRedirectUri.toString()) }
            .scope(OidcScopes.OPENID)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build(),
            ).tokenSettings(
                TokenSettings
                    .builder()
                    .accessTokenTimeToLive(properties.tokens.accessToken)
                    .refreshTokenTimeToLive(properties.tokens.refreshTokenIdle)
                    .reuseRefreshTokens(false)
                    .build(),
            ).build()

    companion object {
        const val WEB = "ogcloud-web"
        const val DESKTOP = "ogcloud-desktop"
        const val MOBILE = "ogcloud-mobile"
    }
}
