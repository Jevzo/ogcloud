package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.text.requireText
import io.ogwars.cloud.text.TextField
import java.time.Instant

enum class RoutingStrategy {
    LEAST_FILLED,
    MOST_FILLED,
    BALANCED,
    ROUND_ROBIN,
}

/** Network-wide settings; texts are in OgCloud's tag format (D-0200). */
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
        requireText("motd", TextField.MOTD, motd)
        requireText("maintenanceMotd", TextField.MAINTENANCE_MOTD, maintenanceMotd)
        requireText("versionName", TextField.VERSION_NAME, versionName)
        requireText("maintenanceVersionName", TextField.MAINTENANCE_VERSION_NAME, maintenanceVersionName)
        requireText("tablistHeader", TextField.TABLIST_HEADER, tablistHeader)
        requireText("tablistFooter", TextField.TABLIST_FOOTER, tablistFooter)
        requireText("maintenanceKickMessage", TextField.MAINTENANCE_KICK_MESSAGE, maintenanceKickMessage)
    }

    val eulaAccepted: Boolean get() = eulaAcceptedAt != null
}
