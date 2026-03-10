package io.ogwars.cloud.velocity.notification

import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.permission.PermissionCache
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class AdminNotificationManager(
    private val proxyServer: ProxyServer,
    private val permissionCache: PermissionCache,
) {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun notifyServerLifecycle(
        displayName: String?,
        state: String,
        group: String,
    ) {
        val name = displayName ?: VelocityMessages.Common.UNKNOWN
        broadcast(
            VelocityMessages.format(
                VelocityMessages.Notification.SERVER_LIFECYCLE,
                "prefix" to VelocityMessages.Prefix.ADMIN,
                "display_name" to name,
                "state" to state,
                "group" to group,
            ),
        )
    }

    fun notifyNetworkMaintenance(enabled: Boolean) {
        val status =
            if (enabled) {
                VelocityMessages.Notification.STATUS_ENABLED
            } else {
                VelocityMessages.Notification.STATUS_DISABLED
            }

        broadcast(
            VelocityMessages.format(
                VelocityMessages.Notification.NETWORK_MAINTENANCE,
                "prefix" to VelocityMessages.Prefix.ADMIN,
                "status" to status,
            ),
        )
    }

    fun notifyGroupMaintenance(
        group: String,
        enabled: Boolean,
    ) {
        val status =
            if (enabled) {
                VelocityMessages.Notification.STATUS_ENABLED
            } else {
                VelocityMessages.Notification.STATUS_DISABLED
            }

        broadcast(
            VelocityMessages.format(
                VelocityMessages.Notification.GROUP_MAINTENANCE,
                "prefix" to VelocityMessages.Prefix.ADMIN,
                "group" to group,
                "status" to status,
            ),
        )
    }

    private fun broadcast(message: String) {
        val component = legacySerializer.deserialize(message)

        proxyServer.allPlayers.forEach { player ->
            if (permissionCache.hasPermission(player.uniqueId, NOTIFY_PERMISSION)) {
                player.sendMessage(component)
            }
        }
    }

    companion object {
        private const val NOTIFY_PERMISSION = "ogcloud.notify"
    }
}
