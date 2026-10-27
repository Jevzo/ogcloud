package io.ogwars.cloud.controller.auth

import io.ogwars.cloud.controller.config.GrpcAuthProperties
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.health.v1.HealthGrpc
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.stereotype.Component

/** Every internal gRPC call carries a projected service-account token of an allowed caller (D-0013); health checks are open. */
@Component
@GlobalServerInterceptor
class ServiceAccountAuthInterceptor(
    private val reviewer: TokenReviewer,
    private val properties: GrpcAuthProperties,
) : ServerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun <Q, R> interceptCall(
        call: ServerCall<Q, R>,
        headers: Metadata,
        next: ServerCallHandler<Q, R>,
    ): ServerCall.Listener<Q> {
        if (call.methodDescriptor.serviceName == HealthGrpc.SERVICE_NAME) return next.startCall(call, headers)
        val token = headers.get(AUTHORIZATION)?.removePrefix(BEARER)?.takeIf { it.isNotBlank() }
        val caller = token?.let(reviewer::username)
        val denial =
            when {
                caller == null -> {
                    Status.UNAUTHENTICATED.withDescription("A valid service-account token is required")
                }

                caller !in properties.allowedCallers -> {
                    Status.PERMISSION_DENIED.withDescription(
                        "$caller may not call ${call.methodDescriptor.serviceName}",
                    )
                }

                else -> {
                    return next.startCall(call, headers)
                }
            }
        log.warn("Rejected {} call: {}", call.methodDescriptor.fullMethodName, denial.description)
        call.close(denial, Metadata())
        return object : ServerCall.Listener<Q>() {}
    }

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        const val BEARER = "Bearer "
    }
}
