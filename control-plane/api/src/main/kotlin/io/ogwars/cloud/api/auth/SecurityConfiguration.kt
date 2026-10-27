package io.ogwars.cloud.api.auth

import io.ogwars.cloud.api.auth.mfa.MfaGate
import io.ogwars.cloud.api.auth.mfa.MfaRequirements
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher

/** Three chains: the authorization server, the bearer-token API, and the login pages (D-0098, D-0083). */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    @Order(1)
    fun authorizationServerChain(
        http: HttpSecurity,
        settings: AuthorizationServerSettings,
        clients: RegisteredClientRepository,
        tokens: OAuth2TokenGenerator<OAuth2Token>,
        mfa: MfaRequirements,
    ): SecurityFilterChain {
        val authorizationServer = OAuth2AuthorizationServerConfigurer()
        val post = PathPatternRequestMatcher.withDefaults()
        val authorizationEndpoint = PathPatternRequestMatcher.withDefaults().matcher(settings.authorizationEndpoint)
        http
            .securityMatcher(authorizationServer.endpointsMatcher)
            .with(authorizationServer) { server ->
                server.tokenGenerator(tokens)
                server.oidc(Customizer.withDefaults())
                server.clientAuthentication { authentication ->
                    authentication.authenticationConverter(
                        PublicClientTokenRequests(
                            post.matcher(HttpMethod.POST, settings.tokenEndpoint),
                            post.matcher(HttpMethod.POST, settings.tokenRevocationEndpoint),
                        ),
                    )
                    authentication.authenticationProvider(PublicClientTokenRequests.Authenticator(clients))
                }
            }.authorizeHttpRequests { it.anyRequest().authenticated() }
            // Before the authorization endpoint issues a code (D-0097).
            .addFilterAfter(MfaGate(authorizationEndpoint, mfa), SecurityContextHolderFilter::class.java)
            // The OIDC user info endpoint takes the caller's access token.
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint(LOGIN_PAGE),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML),
                )
            }
        return http.build()
    }

    @Bean
    @Order(2)
    fun apiChain(
        http: HttpSecurity,
        tokens: JwtAuthenticationConverter,
    ): SecurityFilterChain =
        http
            .securityMatcher("$API_PATHS/**")
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "$API_PATHS/openapi", "$API_PATHS/openapi.yaml").permitAll()
                // Creates the first owner with the setup token (D-0094).
                it.requestMatchers("$API_PATHS/v1/setup").permitAll()
                it.anyRequest().authenticated()
            }.oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(tokens) } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Bearer tokens only: no cookie authenticates an API call, so there is nothing to forge.
            .csrf { it.disable() }
            .build()

    @Bean
    @Order(3)
    fun loginChain(
        http: HttpSecurity,
        properties: AuthProperties,
    ): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/**", "/actuator/prometheus", "/error").permitAll()
                it.anyRequest().authenticated()
            }.formLogin(Customizer.withDefaults())
            // Passkeys belong to the public origin (D-0085, D-0098).
            .webAuthn {
                it.rpName(RELYING_PARTY)
                it.rpId(properties.issuer.host)
                it.allowedOrigins(properties.issuer.toString())
            }.build()

    /** The access token's permissions become authorities without a prefix, e.g. `groups:write` (D-0092). */
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(
                JwtGrantedAuthoritiesConverter().apply {
                    setAuthoritiesClaimName(TokenClaims.PERMISSIONS_CLAIM)
                    setAuthorityPrefix("")
                },
            )
        }

    private companion object {
        const val LOGIN_PAGE = "/login"
        const val API_PATHS = "/api"
        const val RELYING_PARTY = "OgCloud"
    }
}
