package io.ogwars.cloud.velocity.network

class NetworkState(
    @Volatile var maintenance: Boolean = false,
    @Volatile var maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    @Volatile var maxPlayers: Int = 1000,
    @Volatile var defaultGroup: String = "lobby",
    @Volatile var permissionSystemEnabled: Boolean = true,
    @Volatile var tablistEnabled: Boolean = true,
) {
    fun update(
        maintenance: Boolean,
        maintenanceKickMessage: String,
        maxPlayers: Int,
        defaultGroup: String,
        permissionSystemEnabled: Boolean,
        tablistEnabled: Boolean,
    ) {
        this.maintenance = maintenance
        this.maintenanceKickMessage = maintenanceKickMessage
        this.maxPlayers = maxPlayers
        this.defaultGroup = defaultGroup
        this.permissionSystemEnabled = permissionSystemEnabled
        this.tablistEnabled = tablistEnabled
    }
}
