package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.SessionResponse
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** An in-process gateway that welcomes every session and records what nodes send. */
class FakeGateway : AutoCloseable {
    class Session(
        val authorization: String?,
        val responses: StreamObserver<SessionResponse>,
    ) {
        val requests = LinkedBlockingQueue<SessionRequest>()

        fun next(): SessionRequest = requests.poll(WAIT_SECONDS, TimeUnit.SECONDS) ?: error("nothing arrived")

        fun send(response: SessionResponse) = responses.onNext(response)
    }

    val sessions = LinkedBlockingQueue<Session>()

    /** False makes new sessions wait for the test instead of being welcomed. */
    @Volatile
    var welcomes = true
    private val name = InProcessServerBuilder.generateName()
    private val authorization = io.grpc.Context.key<String?>("authorization")

    private val service =
        object : GatewayServiceGrpc.GatewayServiceImplBase() {
            override fun session(responses: StreamObserver<SessionResponse>): StreamObserver<SessionRequest> {
                val session = Session(authorization.get(), responses)
                return object : StreamObserver<SessionRequest> {
                    override fun onNext(request: SessionRequest) {
                        session.requests += request
                        if (request.hasHello()) {
                            if (welcomes) responses.onNext(WELCOME)
                            sessions += session
                        }
                    }

                    override fun onError(t: Throwable) = Unit

                    override fun onCompleted() = responses.onCompleted()
                }
            }
        }

    private val server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(ServerInterceptors.intercept(service, captureAuthorization()))
            .build()
            .start()

    val channel = InProcessChannelBuilder.forName(name).directExecutor().build()

    fun nextSession(): Session = sessions.poll(WAIT_SECONDS, TimeUnit.SECONDS) ?: error("no session opened")

    private fun captureAuthorization() =
        object : ServerInterceptor {
            override fun <Q, R> interceptCall(
                call: ServerCall<Q, R>,
                headers: Metadata,
                next: ServerCallHandler<Q, R>,
            ): ServerCall.Listener<Q> {
                val value = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER))
                return Contexts.interceptCall(
                    io.grpc.Context
                        .current()
                        .withValue(authorization, value),
                    call,
                    headers,
                    next,
                )
            }
        }

    override fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    companion object {
        const val WAIT_SECONDS = 10L
        private val WELCOME = SessionResponse.newBuilder().setWelcome(Welcome.newBuilder().setServerId("s-1")).build()
    }
}
