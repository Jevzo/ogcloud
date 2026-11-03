package io.ogwars.cloud.npc

import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.InteractionHand
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity

/**
 * Hands clicks on fake entities to the hub. Attacks are left clicks; of a right click's two packets only the plain
 * main-hand interaction counts, so one click acts once.
 */
class NpcClicks(
    private val hub: NpcHub,
) : PacketListener {
    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType != PacketType.Play.Client.INTERACT_ENTITY) return
        val packet = WrapperPlayClientInteractEntity(event)
        val right =
            when (packet.action) {
                WrapperPlayClientInteractEntity.InteractAction.ATTACK -> {
                    false
                }

                WrapperPlayClientInteractEntity.InteractAction.INTERACT -> {
                    if (packet.hand ==
                        InteractionHand.MAIN_HAND
                    ) {
                        true
                    } else {
                        return
                    }
                }

                else -> {
                    return
                }
            }
        val player = event.user.uuid ?: return
        hub.clicked(player, packet.entityId, right)
    }
}
