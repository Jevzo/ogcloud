package io.ogwars.cloud.paper.npc

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.player.InteractionHand
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import io.ogwars.cloud.server.api.OgCloudNpcClickType
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class NpcPacketListener(
    private val plugin: JavaPlugin,
    private val npcManager: NpcManager,
) : PacketListenerAbstract() {
    override fun onPacketReceive(event: PacketReceiveEvent) {
        val player = event.getPlayer<Player>() ?: return
        val wrapper = WrapperPlayClientInteractEntity(event)
        val npcId = npcManager.findNpcIdByEntityId(wrapper.entityId) ?: return

        val clickType =
            when (wrapper.action) {
                WrapperPlayClientInteractEntity.InteractAction.ATTACK -> OgCloudNpcClickType.LEFT
                WrapperPlayClientInteractEntity.InteractAction.INTERACT,
                WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT,
                -> {
                    if (wrapper.hand != InteractionHand.MAIN_HAND) {
                        return
                    }

                    OgCloudNpcClickType.RIGHT
                }
            }

        event.isCancelled = true

        plugin.server.scheduler.runTask(
            plugin,
            Runnable { npcManager.handleInteraction(npcId, player, clickType) },
        )
    }
}
