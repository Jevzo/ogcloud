package io.ogwars.cloud.server.api

import org.bukkit.entity.Player

data class OgCloudNpcInteraction(
    val npcId: String,
    val player: Player,
    val clickType: OgCloudNpcClickType,
)

enum class OgCloudNpcClickType {
    LEFT,
    RIGHT,
}
