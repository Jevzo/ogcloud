package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionInjector
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.redis.RedisManager
import io.ogwars.cloud.paper.tablist.TablistTeamManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

class PlayerJoinListener(
    private val plugin: JavaPlugin,
    private val permissionManager: PermissionManager,
    private val tablistTeamManager: TablistTeamManager,
    private val networkFeatureState: NetworkFeatureState,
    private val redisManager: RedisManager,
    private val logger: Logger
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            cachePlayerPermissions(player)
            applyPlayerState(player)
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        tablistTeamManager.removePlayer(player)
        permissionManager.removePlayer(player.uniqueId)
        PermissionInjector.uninject(player, logger)
    }

    private fun cachePlayerPermissions(player: Player) {
        if (!networkFeatureState.permissionSystemEnabled) {
            permissionManager.removePlayer(player.uniqueId)
            return
        }

        val uuid = player.uniqueId
        val session = redisManager.getPlayerData(uuid.toString())

        if (session != null) {
            permissionManager.cachePlayer(uuid, session)
            return
        }

        permissionManager.cachePlayerDefault(uuid)

        logger.warning("No Redis data for player $uuid, using default permissions")
    }

    private fun applyPlayerState(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            if (networkFeatureState.permissionSystemEnabled) {
                PermissionInjector.inject(player, permissionManager, logger)
            } else {
                PermissionInjector.uninject(player, logger)
            }

            if (networkFeatureState.tablistEnabled) {
                tablistTeamManager.setTablistForMe(player)
                tablistTeamManager.setTablistForOthers(player)
            } else {
                tablistTeamManager.removePlayer(player)
            }
        })
    }
}
