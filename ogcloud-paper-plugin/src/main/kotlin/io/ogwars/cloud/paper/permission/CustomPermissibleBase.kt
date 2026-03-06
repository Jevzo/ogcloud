package io.ogwars.cloud.paper.permission

import org.bukkit.entity.Player
import org.bukkit.permissions.PermissibleBase
import org.bukkit.permissions.Permission

class CustomPermissibleBase(
    private val player: Player,
    private val permissionManager: PermissionManager
) : PermissibleBase(player) {

    override fun hasPermission(permission: Permission): Boolean {
        return permissionManager.hasPermission(player.uniqueId, permission.name) || super.hasPermission(permission)
    }

    override fun hasPermission(permission: String): Boolean {
        return permissionManager.hasPermission(player.uniqueId, permission) || super.hasPermission(permission)
    }
}
