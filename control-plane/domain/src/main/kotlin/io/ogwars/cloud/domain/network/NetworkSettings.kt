package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.text.FieldText
import io.ogwars.cloud.domain.text.requireTexts
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
    }

    val eulaAccepted: Boolean get() = eulaAcceptedAt != null

    /** Refuses texts their fields may not hold, before the settings are saved (D-0205). */
    fun requireValidTexts() =
        requireTexts(
            FieldText("motd", TextField.MOTD, motd),
            FieldText("maintenanceMotd", TextField.MAINTENANCE_MOTD, maintenanceMotd),
            FieldText("versionName", TextField.VERSION_NAME, versionName),
            FieldText("maintenanceVersionName", TextField.MAINTENANCE_VERSION_NAME, maintenanceVersionName),
            FieldText("tablistHeader", TextField.TABLIST_HEADER, tablistHeader),
            FieldText("tablistFooter", TextField.TABLIST_FOOTER, tablistFooter),
            FieldText("maintenanceKickMessage", TextField.MAINTENANCE_KICK_MESSAGE, maintenanceKickMessage),
        )
}
