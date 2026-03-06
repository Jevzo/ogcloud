package io.ogwars.cloud.velocity.notification

import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.velocity.permission.PermissionCache
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class AdminNotificationManager(
    private val proxyServer: ProxyServer,
    private val permissionCache: PermissionCache
) {

    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun notifyServerLifecycle(displayName: String?, state: String, group: String) {
        val name = displayName ?: "unknown"
        broadcast("${PREFIX}Server &f$name &7$state &8(group: &f$group&8)")
    }

    fun notifyNetworkMaintenance(enabled: Boolean) {
        val status = if (enabled) "&cENABLED" else "&aDisabled"
        broadcast("${PREFIX}Network maintenance $status")
    }

    fun notifyGroupMaintenance(group: String, enabled: Boolean) {
        val status = if (enabled) "&cENABLED" else "&aDisabled"
        broadcast("${PREFIX}Group &f\"$group\" &7maintenance $status")
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
        private const val PREFIX = "&8| &6OgCloud &7> "
    }
}
