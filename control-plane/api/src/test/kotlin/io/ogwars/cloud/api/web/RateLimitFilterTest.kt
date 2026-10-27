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
        run("GET", "/api/v1/users")
        run("GET", "/oauth2/jwks")

        taken.map { it.first } shouldBe listOf("auth", "auth", "auth", "api", "api")
        taken.first().third shouldBe limits.auth
        taken.first().second shouldBe "203.0.113.7"
    }

    @Test
    fun `probes and scrapes are never limited`() {
        run("GET", "/actuator/health/readiness").status shouldBe OK

        taken shouldBe emptyList()
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
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest(method, path).apply { remoteAddr = "203.0.113.7" }
        return MockHttpServletResponse().also { filter.doFilter(request, it, MockFilterChain()) }
    }

    private companion object {
        const val OK = 200
        const val TOO_MANY_REQUESTS = 429
    }
}
