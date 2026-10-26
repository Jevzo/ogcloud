package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.GroupId
import java.time.Instant

enum class RoutingStrategy {
    LEAST_FILLED,
    MOST_FILLED,
    BALANCED,
    ROUND_ROBIN,
}

/** Network-wide settings; texts use `&` colour codes. */
data class NetworkSettings(
    val maintenance: Boolean,
    val motd: String,
    val maintenanceMotd: String,
    val versionName: String,
    val maintenanceVersionName: String,
    val maxPlayers: Int,
    val defaultGroupId: GroupId,
    val routingStrategy: RoutingStrategy,
    val permissionSystemEnabled: Boolean,
    val tablistEnabled: Boolean,
    val tablistHeader: String,
    val tablistFooter: String,
    val maintenanceKickMessage: String,
    /** Servers do not start before a network owner accepts the Minecraft EULA (D-0046). */
    val eulaAcceptedAt: Instant?,
    val requireMinecraftLink: Boolean,
) {
    init {
        require(maxPlayers > 0) { "maxPlayers must be positive" }
    }

    val eulaAccepted: Boolean get() = eulaAcceptedAt != null
}
