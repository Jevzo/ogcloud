package io.ogwars.cloud.npc

import com.github.retrooper.packetevents.protocol.player.User
import java.util.UUID

data class NpcLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) {
    fun distanceSquared(other: NpcLocation): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }
}

/** A signed skin texture, as Mojang's session servers return it. */
data class NpcSkin(
    val texture: String,
    val signature: String,
)

/** A player who may see NPCs: where they stand and their PacketEvents connection. */
data class NpcViewer(
    val uuid: UUID,
    val location: NpcLocation,
    val user: User,
)

/** What an NPC shows and what a click does; [title] and [subtitle] are `&`-coded, blank lines are left out. */
data class NpcSpec(
    val id: String,
    val location: NpcLocation,
    val title: String,
    val subtitle: String,
    val skin: NpcSkin?,
    val lookAtPlayers: Boolean,
    val onClick: (player: UUID, rightClick: Boolean) -> Unit,
    /** The group whose live counts fill `%players%` and `%servers%` in the text, if any. */
    val countsOf: String? = null,
)
