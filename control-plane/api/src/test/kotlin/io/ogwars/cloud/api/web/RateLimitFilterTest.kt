package io.ogwars.cloud.api.web

import io.ogwars.cloud.domain.store.RateDecision
import io.ogwars.cloud.domain.store.RateLimit
import io.ogwars.cloud.domain.store.RateLimiter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RateLimitFilterTest {
    private val limits = RateLimitProperties()
    private val taken = mutableListOf<Triple<String, String, RateLimit>>()
    private var decision = RateDecision(true, Duration.ZERO)
    private val limiter =
        RateLimiter { bucket, client, limit, _ ->
            taken += Triple(bucket, client, limit)
            decision
        }
    private val filter = RateLimitFilter(limiter, limits, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

    @Test
    fun `sign-in endpoints use the strict bucket, the rest the API bucket`() {
        run("POST", "/login")
        run("POST", "/oauth2/token")
        run("POST", "/webauthn/authenticate/options")
        run("POST", "/api/v1/me/minecraft-link/confirm")
        run("GET", "/api/v1/users")
        run("GET", "/oauth2/jwks")

        taken.map { it.first } shouldBe listOf("auth", "auth", "auth", "auth", "api", "api")
        taken.first().third shouldBe limits.auth
        taken.first().second shouldBe "203.0.113.7"
    }

    @Test
    fun `password checks behind a token use the strict bucket`() {
        run("DELETE", "/api/v1/me/passkeys/mCrA7k1y0bMq")
        run("PUT", "/api/v1/me/password")
        run("GET", "/login/mfa/totp-setup")
        run("PUT", "/api/v1/me/passkeys/mCrA7k1y0bMq")

        taken.map { it.first } shouldBe listOf("auth", "auth", "auth", "api")
    }

    @Test
    fun `loading the sign-in pages uses their own bucket, and their assets none`() {
        run("GET", "/login")
        run("GET", "/login/state")
        run("GET", "/login/passkeys/new")
        run("GET", "/login/assets/index-Bjo2N0NT.js")

        taken.map { it.first } shouldBe listOf("pages", "pages", "pages")
        taken.first().third shouldBe limits.pages
    }

    @Test
    fun `a token refresh uses its own bucket, other grants the strict one`() {
        run("POST", "/oauth2/token", "grant_type" to "refresh_token")
        run("POST", "/oauth2/token", "grant_type" to "authorization_code")
        run("POST", "/oauth2/token", "grant_type" to "client_credentials")

        taken.map { it.first } shouldBe listOf("refresh", "auth", "auth")
        taken.first().third shouldBe limits.refresh
    }

    @Test
    fun `an empty bucket answers 429 with Retry-After`() {
        decision = RateDecision(false, Duration.ofMillis(1500))

        val response = run("POST", "/login")

        response.status shouldBe TOO_MANY_REQUESTS
        response.getHeader("Retry-After") shouldBe "2"
        response.contentType shouldBe "application/problem+json"
    }

    private fun run(
        method: String,
        path: String,
        vararg parameters: Pair<String, String>,
    ): MockHttpServletResponse {
        val request =
            MockHttpServletRequest(method, path).apply {
                remoteAddr = "203.0.113.7"
                parameters.forEach { (name, value) -> addParameter(name, value) }
            }
        return MockHttpServletResponse().also { filter.doFilter(request, it, MockFilterChain()) }
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}
