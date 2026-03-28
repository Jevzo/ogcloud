package io.ogwars.cloud.paper.npc

import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLocation
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import io.ogwars.cloud.server.api.OgCloudNpcInteraction
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.abs

internal data class NpcEntry(
    val id: String,
    val managed: Boolean,
    val entityId: Int,
    val profileName: String,
    val teamName: String,
    var profileUuid: UUID,
    var definition: NpcDefinition,
    var currentRotation: Rotation,
    val viewers: MutableSet<UUID> = linkedSetOf(),
    val leftSubscribers: MutableMap<UUID, Consumer<OgCloudNpcInteraction>> = linkedMapOf(),
    val rightSubscribers: MutableMap<UUID, Consumer<OgCloudNpcInteraction>> = linkedMapOf(),
    var titleStand: ArmorStand? = null,
    var subtitleStand: ArmorStand? = null,
    var renderedTitle: String? = null,
    var renderedSubtitle: String? = null,
) {
    val worldName: String
        get() = definition.location.world

    fun toBukkitLocation(): Location? = Bukkit.getWorld(worldName)?.let(definition.location::toBukkit)
}

internal data class Rotation(
    val yaw: Float,
    val pitch: Float,
) {
    fun isCloseTo(other: Rotation): Boolean =
        abs(yaw - other.yaw) <= ROTATION_EPSILON &&
            abs(pitch - other.pitch) <= ROTATION_EPSILON

    companion object {
        private const val ROTATION_EPSILON = 1.0f
    }
}

internal data class RuntimeNpcConfig(
    val id: String,
    val location: Location,
    val title: String?,
    val subtitle: String?,
    val model: NpcModel,
    val skin: NpcSkin?,
    val lookAt: NpcLookAtConfig,
    val leftSubscribers: Map<UUID, Consumer<OgCloudNpcInteraction>>,
    val rightSubscribers: Map<UUID, Consumer<OgCloudNpcInteraction>>,
)

internal data class NpcGroupStats(
    val players: Int = 0,
    val servers: Int = 0,
)

internal fun npcLocationFromBukkit(location: Location): NpcLocation =
    NpcLocation(
        world = location.world?.name ?: throw IllegalArgumentException("NPC location world must not be null"),
        x = location.x,
        y = location.y,
        z = location.z,
        yaw = location.yaw,
        pitch = location.pitch,
    )

internal fun NpcLocation.toBukkit(world: World): Location = Location(world, x, y, z, yaw, pitch)
