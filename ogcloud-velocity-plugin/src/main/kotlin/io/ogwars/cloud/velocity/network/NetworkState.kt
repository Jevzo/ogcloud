package io.ogwars.cloud.velocity.network

class NetworkState(
    @Volatile var maintenance: Boolean = false,
    @Volatile var maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    @Volatile var maxPlayers: Int = 1000,
    @Volatile var defaultGroup: String = "lobby"
) {

    fun update(
        maintenance: Boolean,
        maintenanceKickMessage: String,
        maxPlayers: Int,
        defaultGroup: String
    ) {
        this.maintenance = maintenance
        this.maintenanceKickMessage = maintenanceKickMessage
        this.maxPlayers = maxPlayers
        this.defaultGroup = defaultGroup
    }
}
