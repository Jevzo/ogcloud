package io.ogwars.cloud.npc

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.protocol.world.Location
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.EnumSet
import java.util.Optional
import java.util.UUID

/** An NPC as players see it: a fake player plus one floating name per text line. */
class ShownNpc(
    val spec: NpcSpec,
    val bodyId: Int,
    /** The fake player's profile; its name stays hidden through [NpcPackets.TEAM]. */
    val profile: UserProfile,
    /** Entity ids of its non-blank text lines, top first. */
    val lineIds: List<Int>,
)

/**
 * The packets that show NPCs. OgCloud's backends run 1.8 (PandaSpigot) or the current Paper line (D-0006), whose
 * player and entity packets differ, so each gets its own implementation; the platform picks one.
 */
interface NpcPackets {
    /** Once per viewer, before their first NPC: the team that hides NPC name tags. */
    fun prepare(user: User) = user.sendPacket(team())

    fun spawn(
        user: User,
        npc: ShownNpc,
        lines: List<String>,
    )

    fun rename(
        user: User,
        lineId: Int,
        text: String,
    )

    fun look(
        user: User,
        npc: ShownNpc,
        yaw: Float,
        pitch: Float,
    ) {
        user.sendPacket(WrapperPlayServerEntityRotation(npc.bodyId, yaw, pitch, true))
        user.sendPacket(WrapperPlayServerEntityHeadLook(npc.bodyId, yaw))
    }

    fun despawn(
        user: User,
        npc: ShownNpc,
    ) {
        user.sendPacket(WrapperPlayServerDestroyEntities(*(listOf(npc.bodyId) + npc.lineIds).toIntArray()))
    }

    /** 1.8 needs the fake player in the tab list while its skin loads; current clients never list it. */
    fun unlist(
        user: User,
        npc: ShownNpc,
    ) = Unit

    companion object {
        const val TEAM = "ogcloud-npcs"

        /** Height above the NPC's feet of each line's armor stand, title first, so names float over its head. */
        val LINE_HEIGHTS = listOf(0.35, 0.1)

        internal const val INVISIBLE: Byte = 0x20
        internal const val FLAGS_INDEX = 0
        internal const val NAME_INDEX = 2
        internal const val NAME_VISIBLE_INDEX = 3

        internal fun skin(spec: NpcSpec) =
            listOfNotNull(
                spec.skin?.let {
                    TextureProperty("textures", it.texture, it.signature)
                },
            )

        internal fun team() =
            WrapperPlayServerTeams(
                TEAM,
                WrapperPlayServerTeams.TeamMode.CREATE,
                WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.empty(),
                    Component.empty(),
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.NEVER,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE,
                    WrapperPlayServerTeams.OptionData.NONE,
                ),
                emptyList(),
            )

        internal fun joinTeam(name: String) =
            WrapperPlayServerTeams(
                TEAM,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
                name,
            )
    }
}

/** Paper 26.x: unlisted player info, players spawned like any entity, names as components. */
class ModernNpcPackets(
    private val component: (String) -> Component,
) : NpcPackets {
    override fun spawn(
        user: User,
        npc: ShownNpc,
        lines: List<String>,
    ) {
        val location = npc.spec.location
        val info = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(npc.profile, false, 0, GameMode.SURVIVAL, null, null)
        user.sendPacket(
            WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                ),
                info,
            ),
        )
        user.sendPacket(NpcPackets.joinTeam(npc.profile.name))
        val position = Vector3d(location.x, location.y, location.z)
        user.sendPacket(
            WrapperPlayServerSpawnEntity(
                npc.bodyId,
                Optional.of(npc.profile.uuid),
                EntityTypes.PLAYER,
                position,
                location.pitch,
                location.yaw,
                location.yaw,
                0,
                Optional.empty(),
            ),
        )
        user.sendPacket(WrapperPlayServerEntityHeadLook(npc.bodyId, location.yaw))
        npc.lineIds.zip(lines).forEachIndexed { index, (id, text) ->
            val at = Vector3d(location.x, location.y + NpcPackets.LINE_HEIGHTS[index], location.z)
            user.sendPacket(
                WrapperPlayServerSpawnEntity(
                    id,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ARMOR_STAND,
                    at,
                    0f,
                    0f,
                    0f,
                    0,
                    Optional.empty(),
                ),
            )
            user.sendPacket(WrapperPlayServerEntityMetadata(id, line(text)))
        }
    }

    override fun rename(
        user: User,
        lineId: Int,
        text: String,
    ) = user.sendPacket(WrapperPlayServerEntityMetadata(lineId, line(text)))

    override fun despawn(
        user: User,
        npc: ShownNpc,
    ) {
        super.despawn(user, npc)
        user.sendPacket(WrapperPlayServerPlayerInfoRemove(npc.profile.uuid))
    }

    private fun line(text: String): List<EntityData<*>> =
        listOf(
            EntityData(NpcPackets.FLAGS_INDEX, EntityDataTypes.BYTE, NpcPackets.INVISIBLE),
            EntityData(NpcPackets.NAME_INDEX, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(component(text))),
            EntityData(NpcPackets.NAME_VISIBLE_INDEX, EntityDataTypes.BOOLEAN, true),
        )
}

/** PandaSpigot 1.8.8: listed player info while the skin loads, the 1.8 spawn packets, names as legacy text. */
class LegacyNpcPackets : NpcPackets {
    override fun spawn(
        user: User,
        npc: ShownNpc,
        lines: List<String>,
    ) {
        val location = npc.spec.location
        user.sendPacket(WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, playerData(npc)))
        user.sendPacket(NpcPackets.joinTeam(npc.profile.name))
        val at = Location(location.x, location.y, location.z, location.yaw, location.pitch)
        user.sendPacket(WrapperPlayServerSpawnPlayer(npc.bodyId, npc.profile.uuid, at, emptyList()))
        user.sendPacket(WrapperPlayServerEntityHeadLook(npc.bodyId, location.yaw))
        npc.lineIds.zip(lines).forEachIndexed { index, (id, text) ->
            val position = Vector3d(location.x, location.y + NpcPackets.LINE_HEIGHTS[index], location.z)
            user.sendPacket(
                WrapperPlayServerSpawnLivingEntity(
                    id,
                    UUID.randomUUID(),
                    EntityTypes.ARMOR_STAND,
                    position,
                    0f,
                    0f,
                    0f,
                    Vector3d.zero(),
                    line(text),
                ),
            )
        }
    }

    override fun rename(
        user: User,
        lineId: Int,
        text: String,
    ) = user.sendPacket(WrapperPlayServerEntityMetadata(lineId, line(text)))

    override fun unlist(
        user: User,
        npc: ShownNpc,
    ) = user.sendPacket(WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, playerData(npc)))

    private fun playerData(npc: ShownNpc) =
        WrapperPlayServerPlayerInfo.PlayerData(null, npc.profile, GameMode.SURVIVAL, 0)

    private companion object {
        /** `&` before a colour or format code, the only `&` that becomes `§`. */
        val LEGACY_CODE = Regex("&(?=[0-9a-fk-orA-FK-OR])")
    }

    /** 1.8 has no components in metadata: names are `§`-coded strings and the name flag is a byte. */
    private fun line(text: String): List<EntityData<*>> =
        listOf(
            EntityData(NpcPackets.FLAGS_INDEX, EntityDataTypes.BYTE, NpcPackets.INVISIBLE),
            EntityData(NpcPackets.NAME_INDEX, EntityDataTypes.STRING, LEGACY_CODE.replace(text, "§")),
            EntityData(NpcPackets.NAME_VISIBLE_INDEX, EntityDataTypes.BYTE, 1.toByte()),
        )
}
