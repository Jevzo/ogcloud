package io.ogwars.cloud.api.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.web.authentication.AuthenticationConverter
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * PKCE apps refresh and revoke with their client_id alone (D-0090). Spring authenticates public clients only
 * for the code exchange, where the code verifier proves who they are; rotation guards the refresh token.
 */
class PublicClientTokenRequests(
    private val tokenEndpoint: RequestMatcher,
    private val revocationEndpoint: RequestMatcher,
) : AuthenticationConverter {
    override fun convert(request: HttpServletRequest): Authentication? {
        val parameters = request.parameterMap
        val clientId = parameters[OAuth2ParameterNames.CLIENT_ID]?.singleOrNull()
        val isRefresh =
            tokenEndpoint.matches(request) &&
                parameters[OAuth2ParameterNames.GRANT_TYPE]?.singleOrNull() ==
                AuthorizationGrantType.REFRESH_TOKEN.value
        val anonymous =
            request.getHeader(HttpHeaders.AUTHORIZATION) == null &&
                OAuth2ParameterNames.CLIENT_SECRET !in parameters &&
                PkceParameterNames.CODE_VERIFIER !in parameters
        if (clientId == null || !anonymous || !(isRefresh || revocationEndpoint.matches(request))) return null
        return OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, mapOf(MARKER to true))
    }

    /** Accepts what [PublicClientTokenRequests] produced, for clients registered without a secret. */
    class Authenticator(
        private val clients: RegisteredClientRepository,
    ) : AuthenticationProvider {
        override fun authenticate(authentication: Authentication): Authentication? {
            val request = authentication as OAuth2ClientAuthenticationToken
            if (request.additionalParameters[MARKER] != true) return null
            val client =
                clients
                    .findByClientId(request.principal as String)
                    ?.takeIf { ClientAuthenticationMethod.NONE in it.clientAuthenticationMethods }
                    ?: throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)
            return OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null)
        }

        override fun supports(authentication: Class<*>): Boolean =
            OAuth2ClientAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

    private companion object {
        const val MARKER = "io.ogwars.cloud.public-client-token-request"
    }
}
