package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.RotatedTokenStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.service.SecretGenerator
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTypeValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import java.security.SecureRandom
import java.time.Clock

/** The OIDC provider's parts (D-0012): JDBC storage from V2, keys from Infisical, tokens per D-0090. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AuthorizationServerConfiguration {
    @Bean
    fun signingKeys(properties: AuthProperties): SigningKeys =
        SigningKeys.load(properties.signing.keysDir, properties.signing.activeKeyId)

    @Bean
    fun jwkSource(keys: SigningKeys): JWKSource<SecurityContext> = keys.jwkSource()

    @Bean
    fun jwtEncoder(jwks: JWKSource<SecurityContext>): JwtEncoder = NimbusJwtEncoder(jwks)

    @Bean
    fun jwtDecoder(
        jwks: JWKSource<SecurityContext>,
        properties: AuthProperties,
    ): JwtDecoder =
        NimbusJwtDecoder.withJwkSource(jwks).build().apply {
            setJwtValidator(
                JwtValidators.createDefaultWithValidators(
                    JwtIssuerValidator(properties.issuer.toString()),
                    JwtTypeValidator(TokenClaims.ACCESS_TOKEN_TYPE),
                ),
            )
        }

    // Every endpoint sits under the paths the Gateway routes to the API (D-0098).
    @Bean
    fun authorizationServerSettings(properties: AuthProperties): AuthorizationServerSettings =
        AuthorizationServerSettings
            .builder()
            .issuer(properties.issuer.toString())
            .oidcUserInfoEndpoint("/oauth2/userinfo")
            .oidcLogoutEndpoint("/oauth2/logout")
            .build()

    @Bean
    fun registeredClientRepository(jdbc: JdbcOperations): RegisteredClientRepository =
        JdbcRegisteredClientRepository(jdbc)

    @Bean
    fun authorizationService(
        jdbc: JdbcOperations,
        clients: RegisteredClientRepository,
        rotated: RotatedTokenStore,
        clock: Clock,
    ): OAuth2AuthorizationService = RefreshTokenRotation(JdbcOAuth2AuthorizationService(jdbc, clients), rotated, clock)

    @Bean
    fun authorizationConsentService(
        jdbc: JdbcOperations,
        clients: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService = JdbcOAuth2AuthorizationConsentService(jdbc, clients)

    @Bean
    fun tokenClaims(
        keys: SigningKeys,
        users: WebUserStore,
        permissions: UserPermissions,
    ): TokenClaims = TokenClaims(keys.activeKeyId, users, permissions)

    @Bean
    fun tokenGenerator(
        encoder: JwtEncoder,
        claims: TokenClaims,
        properties: AuthProperties,
        secrets: SecretGenerator,
        clock: Clock,
    ): OAuth2TokenGenerator<OAuth2Token> =
        DelegatingOAuth2TokenGenerator(
            JwtGenerator(encoder).apply {
                setJwtCustomizer(claims)
                setClock(clock)
            },
            RefreshTokens(properties.tokens, secrets, clock),
        )

    @Bean
    fun passwordEncoder(): PasswordEncoder = Passwords.encoder()

    @Bean
    fun secretCipher(
        properties: AuthProperties,
        random: SecureRandom,
    ): SecretCipher = SecretCipher.of(properties.totp, random)

    @Bean
    fun passkeyUsers(jdbc: JdbcOperations): PublicKeyCredentialUserEntityRepository =
        JdbcPublicKeyCredentialUserEntityRepository(jdbc)

    @Bean
    fun passkeyCredentials(jdbc: JdbcOperations): UserCredentialRepository = JdbcUserCredentialRepository(jdbc)
}
