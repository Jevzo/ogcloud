package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import org.slf4j.Logger

class InitialServerHandler(
    private val serverRegistry: ServerRegistry,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val logger: Logger,
) {
    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        val selected = selectInitialServer(event.player.uniqueId)
        if (selected == null) {
            logger.warn(
                "No servers available in group '{}' for player {}",
                networkState.defaultGroup,
                event.player.username,
            )
            return
        }

        event.setInitialServer(selected)

        logger.info(
            "Sending player {} to {} ({} players)",
            event.player.username,
            selected.serverInfo.name,
            selected.playersConnected.size,
        )
    }

    private fun selectInitialServer(playerUuid: java.util.UUID) =
        serverRegistry
            .getServersByGroup(
                networkState.defaultGroup,
                includeMaintenance =
                    networkState.permissionSystemEnabled &&
                        permissionCache.hasPermission(
                            playerUuid,
                            MAINTENANCE_BYPASS_PERMISSION,
                        ),
            ).minByOrNull { it.playersConnected.size }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
    }
}
