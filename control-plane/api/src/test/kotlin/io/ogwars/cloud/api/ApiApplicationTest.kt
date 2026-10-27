package io.ogwars.cloud.api

import io.ogwars.cloud.apitest.ProbeController
import io.ogwars.cloud.livestate.ValkeyContainerConfiguration
import io.ogwars.cloud.messaging.KafkaContainerConfiguration
import io.ogwars.cloud.persistence.PostgresContainerConfiguration
import io.ogwars.cloud.service.GoldenFiles
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(
    PostgresContainerConfiguration::class,
    ValkeyContainerConfiguration::class,
    KafkaContainerConfiguration::class,
    ProbeController::class,
)
class ApiApplicationTest(
    @Autowired dataSource: DataSource,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()
    private val jdbc = JdbcClient.create(dataSource)

    @Test
    fun `the API migrates the schema and reports ready`() {
        jdbc.sql("select max(version) from flyway_schema_history").query(String::class.java).single() shouldBe "1"
        get("/actuator/health/readiness").statusCode() shouldBe OK
        get("/actuator/prometheus").body() shouldContain "jvm_threads"
    }

    @Test
    fun `domain failures become problem details`() {
        listOf("missing" to NOT_FOUND, "invalid" to BAD_REQUEST, "stale" to CONFLICT).forEach { (probe, status) ->
            val response = get("/api/v1/probes/$probe")
            response.statusCode() shouldBe status
            response.headers().firstValue("Content-Type").orElse("") shouldBe "application/problem+json"
        }
    }

    @Test
    fun `forwarded client addresses are honoured only from a trusted proxy`() {
        // The test talks to the API from 127.0.0.1, which Tomcat trusts like an in-cluster proxy.
        get("/api/v1/probes/client", "X-Forwarded-For" to "203.0.113.7").body() shouldBe "203.0.113.7"
    }

    @Test
    fun `successful changes are audited with their target`() {
        val response = post("/api/v1/probes/bedwars/actions")

        response.statusCode() shouldBe ACCEPTED
        jdbc
            .sql("select action || ' ' || target_type || ' ' || target_id from audit_log where target_id = 'bedwars'")
            .query(String::class.java)
            .single() shouldBe "POST /api/v1/probes/{probeId}/actions probes bedwars"
    }

    @Test
    fun `the committed OpenAPI contract matches the API`() {
        GoldenFiles.assertMatches(Path.of("../../contracts/openapi/openapi.yaml"), get("/api/openapi.yaml").body())
    }

    private fun get(
        path: String,
        vararg headers: Pair<String, String>,
    ): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(uri(path))
                .apply {
                    headers.forEach { header(it.first, it.second) }
                }.GET(),
        )

    private fun post(path: String): HttpResponse<String> =
        send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()))

    private fun send(request: HttpRequest.Builder) = http.send(request.build(), HttpResponse.BodyHandlers.ofString())

    private fun uri(path: String) = URI("http://localhost:$port$path")

    companion object {
        private const val OK = 200
        private const val ACCEPTED = 202
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val CONFLICT = 409

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            // Object storage is not touched by these tests; the client only needs valid settings.
            registry.add("ogcloud.s3.endpoint") { "http://localhost:1" }
            registry.add("ogcloud.s3.region") { "test" }
            registry.add("ogcloud.s3.bucket") { "ogcloud" }
            registry.add("ogcloud.s3.access-key") { "test" }
            registry.add("ogcloud.s3.secret-key") { "test" }
            registry.add("ogcloud.s3.path-style") { "true" }
        }
    }
}
