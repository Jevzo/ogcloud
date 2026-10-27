package io.ogwars.cloud.controller

import io.ogwars.cloud.contracts.internal.v1.LoginServiceGrpc
import io.ogwars.cloud.contracts.internal.v1.ResolveLoginRequest
import io.ogwars.cloud.controller.leader.LeaderGate
import io.ogwars.cloud.controller.runtime.ArtifactFetcher
import io.ogwars.cloud.livestate.ValkeyContainerConfiguration
import io.ogwars.cloud.messaging.KafkaContainerConfiguration
import io.ogwars.cloud.persistence.PostgresContainerConfiguration
import io.ogwars.cloud.persistence.migrate
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.authentication.TokenRequestBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.MetadataUtils
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Import(
    PostgresContainerConfiguration::class,
    ValkeyContainerConfiguration::class,
    KafkaContainerConfiguration::class,
    ControllerApplicationTest.ClusterConfiguration::class,
)
class ControllerApplicationTest(
    @Autowired private val dataSource: DataSource,
    @Autowired private val leaderGate: LeaderGate,
    @LocalServerPort private val httpPort: Int,
) {
    private val http = HttpClient.newHttpClient()

    // Runs first: the other tests migrate the schema too.
    @Test
    @Order(1)
    fun `stays unready until the API has migrated the schema`() {
        get("/actuator/health/readiness").statusCode() shouldBe SERVICE_UNAVAILABLE

        migrate(dataSource)

        get("/actuator/health/readiness").statusCode() shouldBe OK
    }

    @Test
    fun `leads the cluster and exports the leader metric`() {
        runBlocking { eventually(30.seconds) { leaderGate.isLeader shouldBe true } }

        get("/actuator/prometheus").body() shouldContain "ogcloud_controller_leader 1.0"
    }

    @Test
    fun `serves gRPC over TLS only`() {
        withChannel { channel ->
            HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance()).status shouldBe
                HealthCheckResponse.ServingStatus.SERVING
        }
    }

    @Test
    fun `only the gateway's service account may resolve logins`() {
        migrate(dataSource)
        TestCluster.client().use { client ->
            listOf(GATEWAY_ACCOUNT, OTHER_ACCOUNT).forEach { account ->
                client
                    .serviceAccounts()
                    .inNamespace(
                        NAMESPACE,
                    ).resource(
                        ServiceAccountBuilder()
                            .withNewMetadata()
                            .withName(account)
                            .endMetadata()
                            .build(),
                    ).serverSideApply()
            }
            val gatewayToken = token(client, GATEWAY_ACCOUNT, AUDIENCE)

            withChannel { channel ->
                val login = loginRequest()
                status { LoginServiceGrpc.newBlockingStub(channel).resolveLogin(login) } shouldBe
                    Status.Code.UNAUTHENTICATED
                status { stub(channel, token(client, OTHER_ACCOUNT, AUDIENCE)).resolveLogin(login) } shouldBe
                    Status.Code.PERMISSION_DENIED
                status { stub(channel, token(client, GATEWAY_ACCOUNT, "kubernetes")).resolveLogin(login) } shouldBe
                    Status.Code.UNAUTHENTICATED
                stub(channel, gatewayToken).resolveLogin(login).result.allowed shouldBe true
            }
        }
    }

    private fun loginRequest(): ResolveLoginRequest =
        ResolveLoginRequest
            .newBuilder()
            .setLogin(
                io.ogwars.cloud.contracts.node.v1.ResolveLoginRequest
                    .newBuilder()
                    .setPlayerUuid(UUID.randomUUID().toString())
                    .setPlayerName("Steve"),
            ).setProxyServerId(UUID.randomUUID().toString())
            .build()

    private fun token(
        client: KubernetesClient,
        account: String,
        audience: String,
    ): String =
        client
            .serviceAccounts()
            .inNamespace(NAMESPACE)
            .withName(account)
            .tokenRequest(
                TokenRequestBuilder()
                    .withNewSpec()
                    .withAudiences(
                        audience,
                    ).withExpirationSeconds(TOKEN_SECONDS)
                    .endSpec()
                    .build(),
            ).status.token

    private fun stub(
        channel: ManagedChannel,
        token: String,
    ): LoginServiceGrpc.LoginServiceBlockingStub {
        val headers =
            Metadata().apply {
                put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $token")
            }
        return LoginServiceGrpc
            .newBlockingStub(
                channel,
            ).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
    }

    private fun status(call: () -> Unit): Status.Code = shouldThrow<StatusRuntimeException> { call() }.status.code

    private fun withChannel(use: (ManagedChannel) -> Unit) {
        val channel =
            NettyChannelBuilder
                .forAddress("localhost", grpcPort)
                .sslContext(GrpcSslContexts.forClient().trustManager(TestTls.certificate.toFile()).build())
                .build()
        try {
            use(channel)
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

        /** Bundle sync runs on the leader; tests must not reach Modrinth. */
        @Bean
        @Primary
        fun offlineFetcher(): ArtifactFetcher = ArtifactFetcher { throw IOException("No downloads in tests: $it") }
    }

    companion object {
        private const val OK = 200
        private const val SERVICE_UNAVAILABLE = 503
        private const val CHANNEL_CLOSE_SECONDS = 5L
        private const val NAMESPACE = "ogcloud"
        private const val GATEWAY_ACCOUNT = "ogcloud-gateway"
        private const val OTHER_ACCOUNT = "ogcloud-server"
        private const val AUDIENCE = "ogcloud-controller"
        private const val TOKEN_SECONDS = 600L

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
            // Object storage is not touched by these tests; the client only needs valid settings.
            registry.add("ogcloud.s3.endpoint") { "http://localhost:1" }
            registry.add("ogcloud.s3.region") { "test" }
            registry.add("ogcloud.s3.bucket") { "ogcloud" }
            registry.add("ogcloud.s3.access-key") { "test" }
            registry.add("ogcloud.s3.secret-key") { "test" }
            registry.add("ogcloud.s3.path-style") { "true" }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.location") { "file:${TestTls.keystore}" }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.password") { TestTls.PASSWORD }
            registry.add("spring.ssl.bundle.jks.grpc.keystore.type") { "PKCS12" }
        }
    }
}
