package io.ogwars.cloud.legacy

import io.ogwars.cloud.bukkit.BackendNode
import io.ogwars.cloud.npc.LegacyNpcPackets
import org.bukkit.plugin.java.JavaPlugin

/** OgCloud's PandaSpigot 1.8.8 plugin; everything it shares with Paper lives in the bukkit module (D-0138). */
class OgCloudLegacyPaper : JavaPlugin() {
    private var node: BackendNode? = null

    override fun onEnable() {
        // Kept before starting, so onDisable closes it even when starting fails.
        val started = BackendNode(this, LegacyLine(this), LegacyNpcPackets(), description.version).also { node = it }
        started.start(
            LegacyChat(started.runtime),
            PlayerLimit(server, started.identity.maxPlayers, started.runtime.messages),
        )
    }

    override fun onDisable() {
        node?.close()
        node = null
    }
}
