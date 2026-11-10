package io.ogwars.cloud.api.auth

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Registers the web, desktop and mobile apps as public PKCE clients on every start (D-0012, D-0090), and Grafana
 * as a confidential one when it is configured (D-0153).
 */
@Component
class FirstPartyClients(
    private val clients: RegisteredClientRepository,
    private val properties: AuthProperties,
    private val passwords: PasswordEncoder,
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
        ) + listOfNotNull(properties.clients.grafana?.let(::grafana))

    /**
     * Grafana signs people in with the code flow and its secret and reads their role from the ID token. It gets no
     * refresh token, so its session lasts as long as its access token, which opens nothing on the API (D-0185).
     */
    private fun grafana(grafana: AuthProperties.Grafana): RegisteredClient =
        RegisteredClient
            .from(app(GRAFANA, "Grafana", grafana.redirectUri, null))
            .clientAuthenticationMethods {
                it.clear()
                it.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                it.add(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            }.authorizationGrantTypes { it.remove(AuthorizationGrantType.REFRESH_TOKEN) }
            .clientSecret(passwords.hash(grafana.clientSecret))
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(grafana.session).build())
            .build()

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
        const val GRAFANA = "ogcloud-grafana"

        /** The apps whose users' tokens the API takes (D-0185). */
        val APPS = setOf(WEB, DESKTOP, MOBILE)
    }
}
