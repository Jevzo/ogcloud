package io.ogwars.cloud.domain.group

import java.time.Duration
import java.util.UUID

enum class GroupType {
    /** Autoscaled, disposable servers with a fresh template copy each. */
    DYNAMIC,

    /** At most one server, with a persistent volume. */
    STATIC,
    PROXY,
}

/** A group name. It becomes part of pod names, so it must be a short DNS label. */
@JvmInline
value class GroupId(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) {
            "Group id '$value' must be 1–$MAX_LENGTH lowercase letters, digits or '-', starting with a letter"
        }
    }

    override fun toString(): String = value

    companion object {
        /** Pod names are `<group>-<32 hex>` and Kubernetes caps them at 63 characters. */
        const val MAX_LENGTH = 30
        private val PATTERN = Regex("^[a-z]([a-z0-9-]{0,${MAX_LENGTH - 2}}[a-z0-9])?$")
    }
}

data class ScalingPolicy(
    val minOnline: Int,
    val maxInstances: Int,
    val playersPerServer: Int,
    /** Load ratio (players per capacity, 0–1) above which another server starts. */
    val scaleUpThreshold: Double,
    /** Load ratio below which surplus servers drain. */
    val scaleDownThreshold: Double,
    val cooldown: Duration,
) {
    init {
        require(minOnline >= 0) { "minOnline must not be negative" }
        require(maxInstances >= minOnline) { "maxInstances must be at least minOnline" }
        require(playersPerServer > 0) { "playersPerServer must be positive" }
        require(scaleUpThreshold in 0.0..1.0 && scaleDownThreshold in 0.0..1.0) { "thresholds must be between 0 and 1" }
        require(scaleDownThreshold < scaleUpThreshold) { "scaleDownThreshold must be below scaleUpThreshold" }
        require(!cooldown.isNegative) { "cooldown must not be negative" }
    }
}

/** Kubernetes resource quantities, e.g. "2Gi" or "500m"; a null CPU limit means none. */
data class ResourceSpec(
    val memoryRequest: String,
    val memoryLimit: String,
    val cpuRequest: String,
    val cpuLimit: String? = null,
) {
    init {
        require(listOf(memoryRequest, memoryLimit, cpuRequest).none(String::isBlank)) {
            "Resource quantities must not be blank"
        }
    }
}

/** A custom server image instead of the runtime profile's, and the pull secret it needs. */
data class ImageOverride(
    val image: String,
    val pullSecretRef: String?,
)

data class Group(
    val id: GroupId,
    val type: GroupType,
    val profileId: String,
    val scaling: ScalingPolicy,
    /** How long a draining server waits for players to leave; zero stops immediately. */
    val drainTimeout: Duration,
    val maintenance: Boolean,
    val resources: ResourceSpec = ResourceSpec("1Gi", "2Gi", "500m"),
    val jvmFlags: String = "",
    val imageOverride: ImageOverride? = null,
    /** Volume size of a STATIC group's server, e.g. "10Gi". */
    val storageSize: String? = null,
    val activeTemplateVersionId: UUID? = null,
) {
    init {
        require(type != GroupType.STATIC || scaling.maxInstances <= 1) { "A STATIC group runs at most one server" }
        require(!drainTimeout.isNegative) { "drainTimeout must not be negative" }
    }
}
