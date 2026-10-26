package io.ogwars.cloud.controller

import io.ogwars.cloud.controller.leader.LeaderGate
import io.ogwars.cloud.persistence.PostgresContainerConfiguration
import io.fabric8.kubernetes.client.Config
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfiguration::class, ControllerApplicationTest.ClusterConfiguration::class)
class ControllerApplicationTest(
    @Autowired private val dataSource: DataSource,
    @Autowired private val leaderGate: LeaderGate,
    @LocalServerPort private val httpPort: Int,
) {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `stays unready until the API has migrated the schema`() {
        get("/actuator/health/readiness").statusCode() shouldBe SERVICE_UNAVAILABLE

        Flyway
            .configure()
            .dataSource(dataSource)
            .placeholders(mapOf("controllerRole" to PostgresContainerConfiguration.CONTROLLER_ROLE))
            .load()
            .migrate()

        get("/actuator/health/readiness").statusCode() shouldBe OK
    }

    @Test
    fun `leads the cluster and exports the leader metric`() {
        runBlocking { eventually(30.seconds) { leaderGate.isLeader shouldBe true } }

        get("/actuator/prometheus").body() shouldContain "ogcloud_controller_leader 1.0"
    }

    @Test
    fun `serves gRPC over TLS only`() {
        val channel =
            NettyChannelBuilder
                .forAddress("localhost", grpcPort)
                .sslContext(GrpcSslContexts.forClient().trustManager(TestTls.certificate.toFile()).build())
                .build()
        try {
            HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance()).status shouldBe
                HealthCheckResponse.ServingStatus.SERVING
        } finally {
            channel.shutdownNow().awaitTermination(CHANNEL_CLOSE_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$httpPort$path")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @TestConfiguration(proxyBeanMethods = false)
    class ClusterConfiguration {
        @Bean
        @Primary
        fun testClusterConfig(): Config = TestCluster.config
    }

    companion object {
        private const val OK = 200
        private const val SERVICE_UNAVAILABLE = 503
        private const val CHANNEL_CLOSE_SECONDS = 5L
        private const val NAMESPACE = "ogcloud"

        // Spring gRPC does not publish a port bound to 0, so a free one is picked up front.
        private val grpcPort = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            TestCluster.createNamespace(NAMESPACE)
            registry.add("ogcloud.controller.namespace") { NAMESPACE }
            registry.add("ogcloud.controller.identity") { "controller-test" }
            registry.add("ogcloud.controller.pods.image-registry") { "ghcr.io/jevzo/ogcloud" }
            registry.add("ogcloud.controller.pods.platform-version") { "2.0.0" }
            registry.add("ogcloud.controller.pods.gateway-address") { "ogcloud-gateway.ogcloud.svc:9443" }
            registry.add("spring.grpc.server.port") { grpcPort }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.location") { "file:${TestTls.keystore}" }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.password") { TestTls.PASSWORD }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.type") { "PKCS12" }
        }
    }
}
