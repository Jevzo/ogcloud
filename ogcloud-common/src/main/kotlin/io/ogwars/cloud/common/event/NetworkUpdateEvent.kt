package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.GeneralSettings
import io.ogwars.cloud.common.model.MotdSettings
import io.ogwars.cloud.common.model.TablistSettings
import io.ogwars.cloud.common.model.VersionNameSettings

data class NetworkUpdateEvent(
    val motd: MotdSettings,
    val versionName: VersionNameSettings,
    val maxPlayers: Int,
    val defaultGroup: String,
    val maintenance: Boolean,
    val maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    val tablist: TablistSettings? = null,
    val general: GeneralSettings = GeneralSettings(),
    val timestamp: Long = System.currentTimeMillis(),
)
