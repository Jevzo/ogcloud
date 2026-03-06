package io.ogwars.cloud.velocity.permission

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.PermissionSubject
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.Player

class PermissionCheckListener(
    private val permissionCache: PermissionCache
) {

    @Subscribe
    fun onPermissionsSetup(event: PermissionsSetupEvent) {
        if (event.subject is Player) {
            event.provider = OgCloudPermissionProvider(permissionCache)
        }
    }

    private class OgCloudPermissionProvider(
        private val permissionCache: PermissionCache
    ) : PermissionProvider {
        override fun createFunction(subject: PermissionSubject): PermissionFunction {
            val player = subject as? Player ?: return PermissionFunction { Tristate.UNDEFINED }

            return PermissionFunction { permission ->
                if (permissionCache.hasPermission(player.uniqueId, permission)) {
                    Tristate.TRUE
                } else {
                    Tristate.FALSE
                }
            }
        }
    }
}
