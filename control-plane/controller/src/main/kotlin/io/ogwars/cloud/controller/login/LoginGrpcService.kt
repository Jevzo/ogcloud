package io.ogwars.cloud.controller.login

import io.ogwars.cloud.contracts.internal.v1.LoginServiceGrpc
import io.ogwars.cloud.contracts.internal.v1.ResolveLoginRequest
import io.ogwars.cloud.contracts.internal.v1.ResolveLoginResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID

/** The gateway's login resolution (internal gRPC, gateway only). */
@Service
class LoginGrpcService(
    private val resolver: LoginResolver,
) : LoginServiceGrpc.LoginServiceImplBase() {
    override fun resolveLogin(
        request: ResolveLoginRequest,
        response: StreamObserver<ResolveLoginResponse>,
    ) {
        val player = request.login.playerUuid.toUuidOrNull()
        val proxy = request.proxyServerId.toUuidOrNull()
        val name = request.login.playerName
        if (player == null || proxy == null || !MINECRAFT_NAME.matches(name)) {
            response.onError(
                Status.INVALID_ARGUMENT.withDescription("Invalid player uuid, name or proxy id").asRuntimeException(),
            )
            return
        }
        val result = resolver.resolve(player, name, proxy)
        response.onNext(ResolveLoginResponse.newBuilder().setResult(result).build())
        response.onCompleted()
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private companion object {
        /** Java Edition names; Bedrock names arrive prefixed by the proxy (T-0907). */
        val MINECRAFT_NAME = Regex("^[A-Za-z0-9_]{1,16}$")
    }
}
