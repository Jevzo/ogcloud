package io.ogwars.cloud.paper.network

class NetworkFeatureState(
    @Volatile var permissionSystemEnabled: Boolean = true,
    @Volatile var tablistEnabled: Boolean = true
) {

    fun update(permissionSystemEnabled: Boolean, tablistEnabled: Boolean) {
        this.permissionSystemEnabled = permissionSystemEnabled
        this.tablistEnabled = tablistEnabled
    }
}
