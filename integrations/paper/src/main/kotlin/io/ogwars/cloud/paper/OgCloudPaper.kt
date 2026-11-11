package io.ogwars.cloud.paper

import io.ogwars.cloud.bukkit.BackendNode
import io.ogwars.cloud.bukkit.enableOrStop
import io.ogwars.cloud.npc.ModernNpcPackets
import org.bukkit.plugin.java.JavaPlugin

/** OgCloud's Paper plugin; everything it shares with PandaSpigot lives in the bukkit module (D-0138). */
class OgCloudPaper : JavaPlugin() {
    private var node: BackendNode? = null

    override fun onEnable() =
        enableOrStop(server, logger) {
            // Kept before starting, so onDisable closes it even when starting fails.
            val started =
                BackendNode(this, PaperLine(this), ModernNpcPackets(), pluginMeta.version).also {
                    node =
                        it
                }
            server.maxPlayers = started.identity.maxPlayers
            started.start(PaperChat(started.runtime))
        }

    override fun onDisable() {
        node?.close()
        node = null
    }
}
