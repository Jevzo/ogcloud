package io.ogwars.cloud.node.core.query

import io.ogwars.cloud.contracts.node.v1.FindPlayerRequest
import io.ogwars.cloud.contracts.node.v1.FindPlayerResponse
import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc
import io.ogwars.cloud.contracts.node.v1.GetPermissionGroupRequest
import io.ogwars.cloud.contracts.node.v1.GetPermissionGroupResponse
import io.ogwars.cloud.contracts.node.v1.GetServerRequest
import io.ogwars.cloud.contracts.node.v1.GetServerResponse
import io.ogwars.cloud.contracts.node.v1.PlayerLocation
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException

class GrpcNodeQueriesTest {
    private val name = InProcessServerBuilder.generateName()
    private val server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(Gateway())
            .build()
            .start()
    private val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val queries = GrpcNodeQueries(GatewayServiceGrpc.newFutureStub(channel), Duration.ofSeconds(5)) { it.run() }

    @AfterEach
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `answers arrive as futures, and NOT_FOUND means nothing there`() {
        queries.server("s-1").join()?.displayName shouldBe "lobby-789abc"
        queries.server("gone").join() shouldBe null
        queries.findPlayer(UUID.randomUUID()).join() shouldBe null
        queries.findPlayer("Jevzo").join()?.serverId shouldBe "s-1"
    }

    @Test
    fun `other failures fail the future`() {
        val failure = shouldThrow<ExecutionException> { queries.permissionGroup("vip").get() }

        failure.cause
            .shouldBeInstanceOf<StatusRuntimeException>()
            .status.code shouldBe Status.Code.UNAVAILABLE
    }

    private class Gateway : GatewayServiceGrpc.GatewayServiceImplBase() {
        override fun getServer(
            request: GetServerRequest,
            responseObserver: StreamObserver<GetServerResponse>,
        ) {
            if (request.serverId != "s-1") {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
                return
            }
            val info = ServerInfo.newBuilder().setServerId("s-1").setDisplayName("lobby-789abc")
            responseObserver.onNext(GetServerResponse.newBuilder().setServer(info).build())
            responseObserver.onCompleted()
        }

        override fun findPlayer(
            request: FindPlayerRequest,
            responseObserver: StreamObserver<FindPlayerResponse>,
        ) {
            val response = FindPlayerResponse.newBuilder()
            if (request.hasPlayerName()) response.setLocation(PlayerLocation.newBuilder().setServerId("s-1"))
            responseObserver.onNext(response.build())
            responseObserver.onCompleted()
        }

        override fun getPermissionGroup(
            request: GetPermissionGroupRequest,
            responseObserver: StreamObserver<GetPermissionGroupResponse>,
        ) = responseObserver.onError(Status.UNAVAILABLE.asRuntimeException())
    }
}
