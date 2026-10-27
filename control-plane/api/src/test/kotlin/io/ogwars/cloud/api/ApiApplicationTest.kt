package io.ogwars.cloud.api

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestSigningKeys
import io.ogwars.cloud.persistence.SchemaVersionProbe
import io.ogwars.cloud.service.GoldenFiles
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

@ApiTest
class ApiApplicationTest(
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val schema: SchemaVersionProbe,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()
    private val oauth = OAuthHttp(port)
    private val token =
        TestSigningKeys.accessToken(
            TestSigningKeys.key(TestSigningKeys.CURRENT),
            listOf("servers:read"),
        )

    @Test
    fun `the API migrates the schema and reports ready`() {
        schema.isCurrent() shouldBe true
        oauth.get("/actuator/health/readiness").statusCode() shouldBe OK
        oauth.get("/actuator/prometheus").body() shouldContain "jvm_threads"
    }

    @Test
    fun `domain failures become problem details`() {
        listOf("missing" to NOT_FOUND, "invalid" to BAD_REQUEST, "stale" to CONFLICT).forEach { (probe, status) ->
            val response = oauth.getWithToken("/api/v1/probes/$probe", token)
            response.statusCode() shouldBe status
            response.headers().firstValue("Content-Type").orElse("") shouldBe "application/problem+json"
        }
    }

    @Test
    fun `forwarded client addresses are honoured only from a trusted proxy`() {
        // The test talks to the API from 127.0.0.1, which Tomcat trusts like an in-cluster proxy.
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/probes/client"))
                .header("Authorization", "Bearer $token")
                .header("X-Forwarded-For", "203.0.113.7")
                .GET()
        http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body() shouldBe "203.0.113.7"
    }

    @Test
    fun `successful changes are audited with the caller and target`() {
        val response = oauth.postWithToken("/api/v1/probes/bedwars/actions", token)

        response.statusCode() shouldBe ACCEPTED
        jdbc
            .sql(
                "select actor_type || ':' || actor_id || ' ' || action || ' ' || target_type || ' ' || target_id " +
                    "from audit_log where target_id = 'bedwars'",
            ).query(String::class.java)
            .single() shouldBe "CLIENT:test-client POST /api/v1/probes/{probeId}/actions probes bedwars"
    }

    @Test
    fun `the committed OpenAPI contract matches the API`() {
        GoldenFiles.assertMatches(
            Path.of("../../contracts/openapi/openapi.yaml"),
            oauth.get("/api/openapi.yaml").body(),
        )
    }

    private companion object {
        const val OK = 200
        const val ACCEPTED = 202
        const val BAD_REQUEST = 400
        const val NOT_FOUND = 404
        const val CONFLICT = 409
    }
}
