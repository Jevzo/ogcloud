package io.ogwars.cloud.api.auth

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RefreshTokensTest {
    private val loginAt = Instant.parse("2026-10-01T12:00:00Z")
    private val lifetimes = AuthProperties.Tokens()

    @Test
    fun `a fresh login gets the full idle lifetime`() {
        val token = generate(now = loginAt).shouldNotBeNull()

        token.issuedAt shouldBe loginAt
        token.expiresAt shouldBe loginAt.plus(lifetimes.refreshTokenIdle)
        token.tokenValue shouldBe "secret-1"
    }

    @Test
    fun `refreshing never extends a login past its maximum age`() {
        val now = loginAt.plus(lifetimes.maxLoginAge).minus(Duration.ofDays(2))

        generate(now)?.expiresAt shouldBe loginAt.plus(lifetimes.maxLoginAge)
    }

    @Test
    fun `other token types are left to other generators`() {
        generate(loginAt, OAuth2TokenType.ACCESS_TOKEN) shouldBe null
    }

    private fun generate(
        now: Instant,
        type: OAuth2TokenType = OAuth2TokenType.REFRESH_TOKEN,
    ) = RefreshTokens(lifetimes, { "secret-1" }, Clock.fixed(now, ZoneOffset.UTC)).generate(
        DefaultOAuth2TokenContext
            .builder()
            .registeredClient(CLIENT)
            .tokenType(type)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorization(
                OAuth2Authorization
                    .withRegisteredClient(CLIENT)
                    .principalName("someone")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .token(OAuth2AuthorizationCode("code", loginAt, loginAt.plusSeconds(CODE_SECONDS)))
                    .build(),
            ).build(),
    )

    private companion object {
        const val CODE_SECONDS = 300L
        val CLIENT: RegisteredClient =
            RegisteredClient
                .withId("app")
                .clientId("app")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1/callback")
                .build()
    }
}
