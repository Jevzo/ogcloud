package io.ogwars.cloud.api.model

data class NetworkSettingsDocument(
    val id: String = "global",
    val motd: MotdSettings = MotdSettings(),
    val versionName: VersionNameSettings = VersionNameSettings(),
    val maxPlayers: Int = 1000,
    val defaultGroup: String = "lobby",
    val maintenance: Boolean = false,
    val maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    val tablist: TablistSettings = TablistSettings(),
    val general: GeneralSettings = GeneralSettings(),
)
