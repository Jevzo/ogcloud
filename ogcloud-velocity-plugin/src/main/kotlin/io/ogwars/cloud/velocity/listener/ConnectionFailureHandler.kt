package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.*

class ConnectionFailureHandler(
    private val serverRegistry: ServerRegistry,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val logger: Logger,
) {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

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

        event.result =
            KickedFromServerEvent.Notify.create(
                legacySerializer.deserialize(VelocityMessages.Listener.ConnectionFailure.CONNECTION_LOST),
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
                fallback.serverInfo.name,
            )
            return
        }

        event.result =
            KickedFromServerEvent.DisconnectPlayer.create(
                legacySerializer.deserialize(VelocityMessages.Listener.ConnectionFailure.NO_SERVERS),
            )
    }

    private fun selectFallbackServer(playerUuid: UUID) =
        serverRegistry
            .getServersByGroup(
                networkState.defaultGroup,
                includeMaintenance =
                    networkState.permissionSystemEnabled &&
                        permissionCache.hasPermission(playerUuid, MAINTENANCE_BYPASS_PERMISSION),
            ).minByOrNull { it.playersConnected.size }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
    }
}
