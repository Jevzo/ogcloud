package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.npc.NpcManager
import io.ogwars.cloud.server.api.OgCloudNpcClickType
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction
import org.bukkit.plugin.java.JavaPlugin

class NpcPacketListener(
    private val javaPlugin: JavaPlugin,
    private val npcManager: NpcManager,
) : PacketAdapter(
        params(javaPlugin, PacketType.Play.Client.USE_ENTITY).optionAsync(),
    ) {
    override fun onPacketReceiving(event: PacketEvent) {
        val player = event.player ?: return
        val packet = event.packet
        val npcId = npcManager.findNpcIdByEntityId(packet.integers.read(0)) ?: return
        val clickType = resolveClickType(packet) ?: return

        event.isCancelled = true

        javaPlugin.server.scheduler.runTask(
            javaPlugin,
            Runnable {
                if (player.isOnline) {
                    npcManager.handleInteraction(npcId, player, clickType)
                }
            },
        )
    }

    private fun resolveClickType(packet: PacketContainer): OgCloudNpcClickType? {
        val useAction: WrappedEnumEntityUseAction =
            runCatching { packet.enumEntityUseActions.read(0) }.getOrNull() ?: return null

        return when (useAction.action) {
            EnumWrappers.EntityUseAction.ATTACK -> OgCloudNpcClickType.LEFT
            EnumWrappers.EntityUseAction.INTERACT ->
                if (useAction.hand == EnumWrappers.Hand.MAIN_HAND) {
                    OgCloudNpcClickType.RIGHT
                } else {
                    null
                }

            else -> null
        }
    }
}
