package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.MotdSettings
import io.ogwars.cloud.api.model.TablistSettings
import io.ogwars.cloud.api.model.VersionNameSettings

data class NetworkUpdateEvent(
    val motd: MotdSettings,
    val versionName: VersionNameSettings,
    val maxPlayers: Int,
    val defaultGroup: String,
    val maintenance: Boolean,
    val maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    val tablist: TablistSettings? = null,
    val timestamp: Long = System.currentTimeMillis()
)
