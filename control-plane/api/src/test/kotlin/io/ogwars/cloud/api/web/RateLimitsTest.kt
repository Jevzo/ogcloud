package io.ogwars.cloud.api.web

import io.ogwars.cloud.apitest.ApiTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.random.Random

/** The token buckets in the running API, keyed by the address the trusted proxy forwards (D-0097, D-0108). */
@ApiTest
@TestPropertySource(properties = ["ogcloud.rate-limits.auth.capacity=2", "ogcloud.rate-limits.auth.per-minute=1"])
class RateLimitsTest(
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `sign-ins beyond the burst get 429 per client address`() {
        // TEST-NET-2 addresses: never real clients, and fresh per run so no earlier bucket interferes.
        val host = Random.nextInt(1, LAST_HOST)
        val client = "198.51.100.$host"

        val statuses = (1..3).map { signIn(client).statusCode() }

        statuses.take(2).none { it == TOO_MANY_REQUESTS } shouldBe true
        statuses.last() shouldBe TOO_MANY_REQUESTS
        signIn(client).headers().firstValue("Retry-After").isPresent shouldBe true
        signIn("198.51.100.${host + 1}").statusCode() shouldNotBe TOO_MANY_REQUESTS
    }

    private fun signIn(client: String): HttpResponse<String> =
        http.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/login"))
                .header("X-Forwarded-For", client)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=nobody&password=wrong"))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val LAST_HOST = 254
    }
}
