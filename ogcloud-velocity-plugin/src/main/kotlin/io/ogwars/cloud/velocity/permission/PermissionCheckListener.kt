package io.ogwars.cloud.velocity.permission

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.PermissionSubject
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.Player
import io.ogwars.cloud.velocity.network.NetworkState

class PermissionCheckListener(
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
) {
    @Subscribe
    fun onPermissionsSetup(event: PermissionsSetupEvent) {
        if (event.subject is Player) {
            event.provider = OgCloudPermissionProvider(permissionCache, networkState)
        }
    }

    private class OgCloudPermissionProvider(
        private val permissionCache: PermissionCache,
        private val networkState: NetworkState,
    ) : PermissionProvider {
        override fun createFunction(subject: PermissionSubject): PermissionFunction {
            val player = subject as? Player ?: return PermissionFunction { Tristate.UNDEFINED }

            return PermissionFunction { permission ->
                if (!networkState.permissionSystemEnabled) {
                    return@PermissionFunction Tristate.UNDEFINED
                }

                if (permissionCache.hasPermission(player.uniqueId, permission)) {
                    Tristate.TRUE
                } else {
                    Tristate.FALSE
                }
            }
        }
    }
}
