package io.ogwars.cloud.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.server.RegisteredServer
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import java.util.UUID

class ConnectionFailureHandler(
    private val serverRegistry: ServerRegistry,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val logger: Logger
) {

    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        if (event.serverKickReason.isPresent) {
            return
        }

        unregisterDeadServer(event.server)

        if (event.player.currentServer.isEmpty) {
            redirectOrDisconnect(event)
            return
        }

        event.result = KickedFromServerEvent.Notify.create(
            Component.text(CONNECTION_LOST_MESSAGE, NamedTextColor.RED)
        )
    }

    private fun unregisterDeadServer(deadServer: RegisteredServer) {
        val serverId = serverRegistry.findServerIdByRegistered(deadServer) ?: return

        serverRegistry.unregisterServer(serverId)

        logger.warn("Unregistered dead server after connection failure: {}", deadServer.serverInfo.name)
    }

    private fun redirectOrDisconnect(event: KickedFromServerEvent) {
        val fallback = selectFallbackServer(event.player.uniqueId)

        if (fallback != null) {
            event.result = KickedFromServerEvent.RedirectPlayer.create(fallback)

            logger.info(
                "Redirecting {} to fallback server {} after connection failure",
                event.player.username,
                fallback.serverInfo.name
            )
            return
        }

        event.result = KickedFromServerEvent.DisconnectPlayer.create(
            Component.text(NO_SERVERS_MESSAGE, NamedTextColor.RED)
        )
    }

    private fun selectFallbackServer(playerUuid: UUID) = serverRegistry.getServersByGroup(
        networkState.defaultGroup,
        includeMaintenance = permissionCache.hasPermission(playerUuid, MAINTENANCE_BYPASS_PERMISSION)
    ).minByOrNull { it.playersConnected.size }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val NO_SERVERS_MESSAGE = "No available servers. Please try again later."
        private const val CONNECTION_LOST_MESSAGE = "The server you were connecting to went down."
    }
}
