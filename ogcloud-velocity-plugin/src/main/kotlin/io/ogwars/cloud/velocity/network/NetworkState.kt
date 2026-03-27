package io.ogwars.cloud.velocity.network

import io.ogwars.cloud.common.model.ProxyRoutingStrategy

class NetworkState(
    @Volatile var maintenance: Boolean = false,
    @Volatile var maintenanceKickMessage: String = "&cServer is currently in maintenance mode.",
    @Volatile var maxPlayers: Int = 1000,
    @Volatile var defaultGroup: String = "lobby",
    @Volatile var permissionSystemEnabled: Boolean = true,
    @Volatile var tablistEnabled: Boolean = true,
    @Volatile var proxyRoutingStrategy: ProxyRoutingStrategy = ProxyRoutingStrategy.LOAD_BASED,
) {
    fun update(
        maintenance: Boolean,
        maintenanceKickMessage: String,
        maxPlayers: Int,
        defaultGroup: String,
        permissionSystemEnabled: Boolean,
        tablistEnabled: Boolean,
        proxyRoutingStrategy: ProxyRoutingStrategy,
    ) {
        this.maintenance = maintenance
        this.maintenanceKickMessage = maintenanceKickMessage
        this.maxPlayers = maxPlayers
        this.defaultGroup = defaultGroup
        this.permissionSystemEnabled = permissionSystemEnabled
        this.tablistEnabled = tablistEnabled
        this.proxyRoutingStrategy = proxyRoutingStrategy
    }
}
