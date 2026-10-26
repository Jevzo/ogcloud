package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** How game server pods are built; the chart sets these per install (D-0019). */
@ConfigurationProperties("ogcloud.controller.pods")
data class PodProperties(
    /** Where OgCloud's images live, e.g. `ghcr.io/jevzo/ogcloud`. */
    val imageRegistry: String,
    /** The platform release; image tags end with it (D-0029). */
    val platformVersion: String,
    /** The gateway's in-cluster address, e.g. `ogcloud-gateway.ogcloud.svc:9443`. */
    val gatewayAddress: String,
    val namespace: String = "ogcloud-servers",
    val serviceAccount: String = "ogcloud-server",
    val gatewayAudience: String = "ogcloud-gateway",
    val gatewayTokenTtl: Duration = Duration.ofHours(1),
    val forwardingSecret: String = "ogcloud-forwarding",
    /** Image prefixes a group's image override may use; empty forbids overrides. */
    val allowedImagePrefixes: List<String> = emptyList(),
    val staticStorageSize: String = "10Gi",
    val staticStorageClass: String? = null,
    /** Long enough for a STATIC server to push its template on shutdown (D-0042). */
    val terminationGracePeriod: Duration = Duration.ofSeconds(90),
    /** The tainted game node pool on Hetzner (D-0047); unset on minikube. */
    val gamePool: GamePool? = null,
    val priorityClasses: PriorityClasses = PriorityClasses(),
) {
    init {
        require(gatewayTokenTtl >= MIN_TOKEN_TTL) { "gatewayTokenTtl must be at least 10 minutes (Kubernetes minimum)" }
    }

    data class GamePool(
        val nodeLabel: String,
        val nodeLabelValue: String,
        val taintKey: String,
        val taintValue: String,
    )

    /** Ordered platform > proxies > default group > game (D-0056); the chart creates them. */
    data class PriorityClasses(
        val proxy: String = "ogcloud-proxy",
        val defaultGroup: String = "ogcloud-default-group",
        val game: String = "ogcloud-game",
    )

    private companion object {
        val MIN_TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }
}
