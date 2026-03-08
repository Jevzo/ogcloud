package io.ogwars.cloud.velocity.tablist

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TablistManager(
    private val proxyServer: ProxyServer,
    private val networkState: NetworkState,
    private val permissionCache: PermissionCache,
    private val serverRegistry: ServerRegistry,
    private val proxyDisplayName: String,
    private val logger: Logger
) {

    @Volatile
    var headerTemplate: String = ""

    @Volatile
    var footerTemplate: String = ""

    @Volatile
    private var enabled: Boolean = true

    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private lateinit var scheduler: ScheduledExecutorService

    fun start() {
        scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ogcloud-tablist").apply { isDaemon = true }
        }
        scheduler.scheduleAtFixedRate(
            ::refreshAll,
            REFRESH_INTERVAL_SECONDS,
            REFRESH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
        logger.info("Tablist manager started (interval={}s)", REFRESH_INTERVAL_SECONDS)
    }

    fun stop() {
        if (::scheduler.isInitialized) {
            scheduler.shutdown()
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled

        if (!enabled) {
            clearAll()
        }
    }

    fun sendTablist(player: Player) {
        if (!enabled) {
            return
        }

        val placeholders = player.createPlaceholders()

        player.sendPlayerListHeaderAndFooter(
            deserializeLegacy(headerTemplate.applyPlaceholders(placeholders)),
            deserializeLegacy(footerTemplate.applyPlaceholders(placeholders))
        )
    }

    private fun refreshAll() {
        if (!enabled) {
            return
        }

        try {
            proxyServer.allPlayers.forEach(::sendTablist)
        } catch (exception: Exception) {
            logger.error("Failed to refresh tablist", exception)
        }
    }

    private fun clearAll() {
        val emptyComponent = deserializeLegacy("")
        proxyServer.allPlayers.forEach { player ->
            player.sendPlayerListHeaderAndFooter(emptyComponent, emptyComponent)
        }
    }

    private fun Player.createPlaceholders(): Map<String, String> {
        val cached = if (networkState.permissionSystemEnabled) {
            permissionCache.getPlayer(uniqueId)
        } else {
            null
        }
        val currentServer = currentServer.orElse(null)
        val serverId = currentServer?.let { serverRegistry.findServerIdByRegistered(it.server) }
        val displayName = serverId?.let(serverRegistry::getDisplayName)
            ?: currentServer?.serverInfo?.name
            ?: NO_SERVER_NAME
        val groupName = serverId?.let(serverRegistry::getGroupForServer) ?: displayName.substringBefore("-")
        val permissionGroupName = if (networkState.permissionSystemEnabled) {
            cached?.groupName ?: DEFAULT_PERMISSION_GROUP
        } else {
            ""
        }

        return mapOf(
            "%server%" to displayName,
            "%group%" to groupName,
            "%proxy%" to proxyDisplayName,
            "%onlinePlayers%" to proxyServer.playerCount.toString(),
            "%maxPlayers%" to networkState.maxPlayers.toString(),
            "%ping%" to ping.toString(),
            "%permissionGroup%" to permissionGroupName
        )
    }

    private fun String.applyPlaceholders(placeholders: Map<String, String>): String {
        var result = this

        for ((placeholder, value) in placeholders) {
            result = result.replace(placeholder, value)
        }

        return result
    }

    private fun deserializeLegacy(text: String) = legacySerializer.deserialize(text.replace('\u00A7', '&'))

    companion object {
        private const val REFRESH_INTERVAL_SECONDS = 2L
        private const val DEFAULT_PERMISSION_GROUP = "Default"
        private const val NO_SERVER_NAME = "none"
    }
}
