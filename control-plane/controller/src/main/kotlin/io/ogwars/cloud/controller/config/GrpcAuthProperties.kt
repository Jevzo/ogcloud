package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Who may call the controller's internal gRPC services (D-0013). */
@ConfigurationProperties("ogcloud.controller.grpc-auth")
data class GrpcAuthProperties(
    /** The audience callers request for their projected token; a token for any other audience is rejected. */
    val audience: String = "ogcloud-controller",
    /** Kubernetes usernames, e.g. `system:serviceaccount:ogcloud:ogcloud-gateway`. */
    val allowedCallers: Set<String> = setOf("system:serviceaccount:ogcloud:ogcloud-gateway"),
    /** How long a successful review is reused; the API server is not asked on every call. */
    val cacheTtl: Duration = Duration.ofMinutes(1),
)
