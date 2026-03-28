package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.message.PaperMessages
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.npc.NpcManager
import io.ogwars.cloud.paper.permission.PermissionInjector
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.redis.RedisManager
import io.ogwars.cloud.paper.tablist.TablistTeamManager
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

class PlayerListener(
    private val plugin: JavaPlugin,
    private val permissionManager: PermissionManager,
    private val tablistTeamManager: TablistTeamManager,
    private val networkFeatureState: NetworkFeatureState,
    private val npcManager: NpcManager,
    private val redisManager: RedisManager,
    private val logger: Logger,
) : Listener {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                cachePlayerPermissions(player)
                applyPlayerState(player)
            },
        )
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        tablistTeamManager.removePlayer(player)
        npcManager.handleViewerQuit(player.uniqueId)
        permissionManager.removePlayer(player.uniqueId)
        PermissionInjector.uninject(player, logger)
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (!networkFeatureState.permissionSystemEnabled) {
            return
        }

        val formatted = buildFormattedMessage(event)
        event.renderer { _, _, _, _ -> deserializeLegacy(formatted) }
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
        plugin.server.scheduler.runTask(
            plugin,
            Runnable {
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
            },
        )
    }

    private fun buildFormattedMessage(event: AsyncChatEvent): String {
        val player = event.player
        val cached = permissionManager.getCachedPlayer(player.uniqueId)
        val prefix = cached?.chatPrefix.orEmpty()
        val nameColor = cached?.nameColor ?: PaperMessages.Chat.DEFAULT_NAME_COLOR
        val suffix = cached?.chatSuffix ?: PaperMessages.Chat.DEFAULT_SUFFIX
        val message = legacySerializer.serialize(event.message())

        return PaperMessages.format(
            PaperMessages.Chat.FORMAT,
            "prefix" to prefix,
            "name_color" to nameColor,
            "player_name" to player.name,
            "suffix" to suffix,
            "message" to message,
        )
    }

    private fun deserializeLegacy(text: String) = legacySerializer.deserialize(text.replace('\u00A7', '&'))
}
