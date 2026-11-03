package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc
import io.grpc.Attributes
import io.grpc.CallCredentials
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.SecurityLevel
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class TokenCredentialsTest {
    private var now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId) = this

            override fun instant() = now
        }

    private class Captured : CallCredentials.MetadataApplier() {
        var headers: Metadata? = null
        var failure: Status? = null

        override fun apply(headers: Metadata) {
            this.headers = headers
        }

        override fun fail(status: Status) {
            failure = status
        }
    }

    private fun request(level: SecurityLevel) =
        object : CallCredentials.RequestInfo() {
            override fun getMethodDescriptor(): MethodDescriptor<*, *> = GatewayServiceGrpc.getGetServerMethod()

            override fun getSecurityLevel() = level

            override fun getAuthority() = "gateway"

            override fun getTransportAttrs(): Attributes = Attributes.EMPTY
        }

    private fun authorization(credentials: TokenCredentials): String? {
        val captured = Captured()
        credentials.applyRequestMetadata(request(SecurityLevel.PRIVACY_AND_INTEGRITY), Runnable::run, captured)
        return captured.headers?.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER))
    }

    @Test
    fun `the token is reread a minute after it was read`() {
        val tokens = ArrayDeque(listOf("first\n", "second"))
        val credentials = TokenCredentials(Path.of("token"), clock) { tokens.removeFirst() }

        val first = authorization(credentials)
        now = now.plus(Duration.ofSeconds(59))
        val cached = authorization(credentials)
        now = now.plus(Duration.ofSeconds(2))
        val reread = authorization(credentials)

        listOf(first, cached, reread) shouldBe listOf("Bearer first", "Bearer first", "Bearer second")
    }

    @Test
    fun `the token never travels without TLS`() {
        val captured = Captured()

        TokenCredentials(Path.of("token"), clock) {
            "secret"
        }.applyRequestMetadata(request(SecurityLevel.NONE), Runnable::run, captured)

        captured.headers shouldBe null
        captured.failure?.code shouldBe Status.Code.UNAUTHENTICATED
    }

    @Test
    fun `settings come from the pod's environment`() {
        val environment =
            mapOf(
                PodEnv.GATEWAY_ADDRESS to "gateway:9443",
                PodEnv.GATEWAY_TOKEN_FILE to "/t/token",
                PodEnv.GATEWAY_CA_FILE to "/t/ca.crt",
            )

        val settings = NodeSettings.fromEnvironment(environment, NodeType.NODE_TYPE_PROXY, "2.0.0")

        settings.gatewayAddress shouldBe "gateway:9443"
        settings.caFile shouldBe Path.of("/t/ca.crt")
        shouldThrow<IllegalStateException> {
            NodeSettings.fromEnvironment(
                emptyMap(),
                NodeType.NODE_TYPE_PROXY,
                "2.0.0",
            )
        }.message shouldBe "${PodEnv.GATEWAY_ADDRESS} is not set"
    }
}
