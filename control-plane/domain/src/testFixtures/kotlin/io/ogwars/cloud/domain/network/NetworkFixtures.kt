package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.GroupId
import java.time.Instant

fun networkSettings(
    maintenance: Boolean = false,
    motd: String = "<green>An OgCloud network",
    maintenanceMotd: String = "<red>Maintenance",
    versionName: String = "OgCloud",
    maintenanceVersionName: String = "Maintenance",
    maxPlayers: Int = 1000,
    defaultGroupId: GroupId = GroupId("lobby"),
    tablistHeader: String = "",
    tablistFooter: String = "",
    maintenanceKickMessage: String = "<red>Maintenance",
    eulaAcceptedAt: Instant? = null,
) = NetworkSettings(
    maintenance = maintenance,
    motd = motd,
    maintenanceMotd = maintenanceMotd,
    versionName = versionName,
    maintenanceVersionName = maintenanceVersionName,
    maxPlayers = maxPlayers,
    defaultGroupId = defaultGroupId,
    routingStrategy = RoutingStrategy.LEAST_FILLED,
    permissionSystemEnabled = true,
    tablistEnabled = false,
    tablistHeader = tablistHeader,
    tablistFooter = tablistFooter,
    maintenanceKickMessage = maintenanceKickMessage,
    eulaAcceptedAt = eulaAcceptedAt,
    requireMinecraftLink = false,
)
