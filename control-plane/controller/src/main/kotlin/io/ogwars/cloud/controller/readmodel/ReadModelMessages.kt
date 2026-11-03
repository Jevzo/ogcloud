package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.ListGroupsResponse
import io.ogwars.cloud.contracts.node.v1.ListNpcsResponse
import io.ogwars.cloud.contracts.node.v1.ListPermissionGroupsResponse
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.toTimestamp
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.livestate.toProto
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

    fun groupList(groups: List<Group>): ListGroupsResponse =
        ListGroupsResponse
            .newBuilder()
            .addAllGroups(
                groups.sortedBy { it.id.value }.map {
                    GroupInfo
                        .newBuilder()
                        .setGroupId(it.id.value)
                        .setGroupType(it.type.toProto())
                        .setMaintenance(it.maintenance)
                        .setProfileId(it.profileId)
                        .setMinOnline(it.scaling.minOnline)
                        .setMaxInstances(it.scaling.maxInstances)
                        .setPlayersPerServer(it.scaling.playersPerServer)
                        .build()
                },
            ).build()

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
            .setIsDefault(g.isDefault)
            .build()
    }

    fun permissionGroupList(groups: List<Revisioned<DomainPermissionGroup>>): ListPermissionGroupsResponse =
        ListPermissionGroupsResponse
            .newBuilder()
            .addAllPermissionGroups(groups.sortedBy { it.value.id }.map(::permissionGroup))
            .build()

    fun playerProfile(player: Player): PlayerProfile =
        PlayerProfile
            .newBuilder()
            .setPlayerUuid(player.uuid.toString())
            .setPlayerName(player.name)
            .setPermissionGroupId(player.rank.groupId)
            .apply { player.rank.expiresAt?.let { setRankExpiresAt(it.toTimestamp()) } }
            .setVersion(player.rankVersion)
            .build()

    fun npcs(npcs: List<Npc>): ListNpcsResponse =
        ListNpcsResponse.newBuilder().addAllNpcs(npcs.map(Npc::toProto)).build()
}
