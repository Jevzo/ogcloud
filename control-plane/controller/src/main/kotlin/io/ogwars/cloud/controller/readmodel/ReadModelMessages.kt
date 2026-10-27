package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.node.v1.ListNpcsResponse
import io.ogwars.cloud.contracts.node.v1.Location
import io.ogwars.cloud.contracts.node.v1.NpcTransfer
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.toTimestamp
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.livestate.toProto
import io.ogwars.cloud.contracts.node.v1.Npc as NpcMessage
import io.ogwars.cloud.contracts.node.v1.NpcAction as NpcActionMessage
import io.ogwars.cloud.domain.permission.PermissionGroup as DomainPermissionGroup

/**
 * The node-facing messages read models store (D-0077). Versions are the database row versions; a node applies an
 * update only when it is newer than its cached entry and drops the entry on a delete.
 */
object ReadModelMessages {
    fun networkSettings(settings: Revisioned<NetworkSettings>): NetworkSettingsUpdated {
        val s = settings.value
        return NetworkSettingsUpdated
            .newBuilder()
            .setVersion(settings.revision)
            .setMaintenance(s.maintenance)
            .setMotd(s.motd)
            .setMaintenanceMotd(s.maintenanceMotd)
            .setVersionName(s.versionName)
            .setMaintenanceVersionName(s.maintenanceVersionName)
            .setMaxPlayers(s.maxPlayers)
            .setDefaultGroupId(s.defaultGroupId.value)
            .setRoutingStrategy(s.routingStrategy.toProto())
            .setPermissionSystemEnabled(s.permissionSystemEnabled)
            .setTablistEnabled(s.tablistEnabled)
            .setTablistHeader(s.tablistHeader)
            .setTablistFooter(s.tablistFooter)
            .setMaintenanceKickMessage(s.maintenanceKickMessage)
            .build()
    }

    fun group(group: Revisioned<Group>): GroupUpdated =
        GroupUpdated
            .newBuilder()
            .setVersion(group.revision)
            .setGroupId(group.value.id.value)
            .setGroupType(group.value.type.toProto())
            .setMaintenance(group.value.maintenance)
            .build()

    fun permissionGroup(group: Revisioned<DomainPermissionGroup>): PermissionGroup {
        val g = group.value
        return PermissionGroup
            .newBuilder()
            .setPermissionGroupId(g.id)
            .setName(g.name)
            .setWeight(g.weight)
            .addAllPermissions(g.permissions.keys.sorted())
            .setChatPrefix(g.display.chatPrefix)
            .setChatSuffix(g.display.chatSuffix)
            .setNameColor(g.display.nameColor)
            .setTabPrefix(g.display.tabPrefix)
            .setVersion(group.revision)
            .build()
    }

    fun playerProfile(player: Player): PlayerProfile =
        PlayerProfile
            .newBuilder()
            .setPlayerUuid(player.uuid.toString())
            .setPlayerName(player.name)
            .setPermissionGroupId(player.rank.groupId)
            .apply { player.rank.expiresAt?.let { setRankExpiresAt(it.toTimestamp()) } }
            .setVersion(player.rankVersion)
            .build()

    fun npcs(npcs: List<Npc>): ListNpcsResponse = ListNpcsResponse.newBuilder().addAllNpcs(npcs.map(::npc)).build()

    private fun npc(npc: Npc): NpcMessage =
        NpcMessage
            .newBuilder()
            .setNpcId(npc.id.toString())
            .setGroupId(npc.groupId.value)
            .setLocation(
                Location
                    .newBuilder()
                    .setWorld(npc.location.world)
                    .setX(npc.location.x)
                    .setY(npc.location.y)
                    .setZ(npc.location.z)
                    .setYaw(npc.location.yaw)
                    .setPitch(npc.location.pitch),
            ).setTitle(npc.title)
            .setSubtitle(npc.subtitle)
            .setSkinTexture(npc.skin?.texture.orEmpty())
            .setSkinSignature(npc.skin?.signature.orEmpty())
            .setLookAtPlayers(npc.lookAtPlayers)
            .apply { npc.leftClick?.let { setLeftClick(action(it)) } }
            .apply { npc.rightClick?.let { setRightClick(action(it)) } }
            .build()

    private fun action(action: NpcAction): NpcActionMessage =
        when (action) {
            is NpcAction.Transfer -> {
                NpcActionMessage
                    .newBuilder()
                    .setTransfer(
                        NpcTransfer
                            .newBuilder()
                            .setGroupId(action.groupId.value)
                            .apply { action.routingStrategy?.let { setRoutingStrategy(it.toProto()) } },
                    ).build()
            }
        }
}
