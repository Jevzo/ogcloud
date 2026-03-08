package io.ogwars.cloud.api.model

data class GeneralSettings(
    val permissionSystemEnabled: Boolean = true,
    val tablistEnabled: Boolean = true,
    val proxyRoutingStrategy: ProxyRoutingStrategy = ProxyRoutingStrategy.LOAD_BASED,
)
