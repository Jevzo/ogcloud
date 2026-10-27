package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.common.sha256Hex
import io.ogwars.cloud.domain.store.RotatedTokenStore
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RefreshTokenRotationTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val remembered = mutableMapOf<String, Pair<String, Duration>>()
    private val rotated =
        object : RotatedTokenStore {
            override fun remember(
                tokenHash: String,
                authorizationId: String,
                ttl: Duration,
            ) {
                remembered[tokenHash] = authorizationId to ttl
            }

            override fun authorizationOf(tokenHash: String): String? = remembered[tokenHash]?.first
        }
    private val delegate = InMemoryOAuth2AuthorizationService()
    private val service = RefreshTokenRotation(delegate, rotated, Clock.fixed(now, ZoneOffset.UTC))
    private val first = authorization(refresh("first", expiresIn = Duration.ofDays(30)))

    @Test
    fun `a rotation remembers the old token until it would have expired`() {
        service.save(first)

        service.save(rotate(first, "second"))

        remembered shouldContainKey sha256Hex("first")
        remembered.getValue(sha256Hex("first")) shouldBe (first.id to Duration.ofDays(30))
        service.findByToken("second", OAuth2TokenType.REFRESH_TOKEN).shouldNotBeNull()
    }

    @Test
    fun `presenting a rotated token revokes the authorization`() {
        service.save(first)
        service.save(rotate(first, "second"))

        service.findByToken("first", OAuth2TokenType.REFRESH_TOKEN) shouldBe null

        delegate.findById(first.id) shouldBe null
        service.findByToken("second", OAuth2TokenType.REFRESH_TOKEN) shouldBe null
    }

    @Test
    fun `saving without a new refresh token remembers nothing`() {
        service.save(first)

        service.save(OAuth2Authorization.from(first).attribute("touched", true).build())

        remembered shouldBe emptyMap()
    }

    @Test
    fun `unknown tokens of other types never revoke anything`() {
        service.save(first)
        service.save(rotate(first, "second"))

        service.findByToken("first", OAuth2TokenType.ACCESS_TOKEN) shouldBe null

        delegate.findById(first.id).shouldNotBeNull()
    }

    private fun rotate(
        authorization: OAuth2Authorization,
        value: String,
    ) = OAuth2Authorization.from(authorization).refreshToken(refresh(value, Duration.ofDays(30))).build()

    private fun refresh(
        value: String,
        expiresIn: Duration,
    ) = OAuth2RefreshToken(value, now, now.plus(expiresIn))

    private fun authorization(refresh: OAuth2RefreshToken) =
        OAuth2Authorization
            .withRegisteredClient(CLIENT)
            .id("authorization-1")
            .principalName("someone")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .refreshToken(refresh)
            .build()

    private companion object {
        val CLIENT: RegisteredClient =
            RegisteredClient
                .withId("app")
                .clientId("app")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1/callback")
                .build()
    }
}
