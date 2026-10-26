package io.ogwars.cloud.domain.npc

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import java.util.UUID

data class Location(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
)

data class NpcSkin(
    val texture: String,
    val signature: String,
)

sealed interface NpcAction {
    /** Sends the clicking player to [groupId]; null routing uses the network default. */
    data class Transfer(
        val groupId: GroupId,
        val routingStrategy: RoutingStrategy?,
    ) : NpcAction
}

/** A managed NPC, shown on every server of [groupId]. */
data class Npc(
    val id: UUID,
    val groupId: GroupId,
    val location: Location,
    val title: String = "",
    val subtitle: String = "",
    val skin: NpcSkin? = null,
    val lookAtPlayers: Boolean = true,
    val leftClick: NpcAction? = null,
    val rightClick: NpcAction? = null,
)
