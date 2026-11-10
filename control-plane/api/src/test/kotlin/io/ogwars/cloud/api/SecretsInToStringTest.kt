package io.ogwars.cloud.api

import io.ogwars.cloud.api.auth.AuthProperties
import io.ogwars.cloud.api.auth.clients.OAuthClientCredentials
import io.ogwars.cloud.api.auth.mfa.TotpEnrollment
import io.ogwars.cloud.api.auth.users.ChangePasswordRequest
import io.ogwars.cloud.api.auth.users.CreateUserRequest
import io.ogwars.cloud.api.auth.users.PasswordRequest
import io.ogwars.cloud.api.auth.users.SetupRequest
import io.ogwars.cloud.api.privacy.PrivacyProperties
import io.ogwars.cloud.domain.common.REDACTED
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path

// Spring logs request and response bodies at DEBUG, and settings may end up in errors.
class SecretsInToStringTest {
    @Test
    fun `settings and bodies never show their secrets`() {
        val totp = AuthProperties.Totp(mapOf("k1" to SECRET), "k1")
        listOf(
            CreateUserRequest("someone", "someone@example.com", SECRET, "viewer"),
            PasswordRequest(SECRET),
            ChangePasswordRequest(SECRET, SECRET),
            SetupRequest(SECRET, "someone", "someone@example.com", SECRET),
            OAuthClientCredentials("machine", SECRET, "Machine", listOf("groups:read")),
            TotpEnrollment(SECRET, "otpauth://totp/OgCloud:someone?secret=$SECRET"),
            PrivacyProperties(SECRET),
            AuthProperties(
                URI.create("https://cloud.example.com"),
                AuthProperties.Signing(Path.of("/keys"), "k1"),
                totp,
                clients = AuthProperties.Clients(grafana = AuthProperties.Grafana(URI.create("https://g"), SECRET)),
                setupToken = SECRET,
            ),
        ).forEach {
            it.toString() shouldNotContain SECRET
            it.toString() shouldContain REDACTED
        }
    }

    private companion object {
        const val SECRET = "s3cret-marker"
    }
}
