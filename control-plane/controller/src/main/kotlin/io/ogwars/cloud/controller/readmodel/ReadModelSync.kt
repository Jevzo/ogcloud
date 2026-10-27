package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.contracts.NETWORK_SUBJECT
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.node.v1.ListNpcsResponse
import io.ogwars.cloud.controller.template.TemplateVersions
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component

/**
 * Writes read models from PostgreSQL and tells the nodes (D-0077). An announced change always broadcasts; a resync
 * broadcasts only what differs from the stored copy, so it can run often without noise (D-0100).
 */
@Component
class ReadModelSync(
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val permissionGroups: PermissionGroupStore,
    private val npcs: NpcStore,
    private val readModels: ReadModelStore,
    private val broadcasts: NodeBroadcasts,
    private val permissions: PlayerPermissions,
    private val sessions: SessionStore,
    private val templates: TemplateVersions,
) {
    fun group(
        id: GroupId,
        deleted: Boolean = false,
        resync: Boolean = false,
    ) {
        val groupWrite = ReadModelWrite.of(readModels, ReadModelKind.GROUP, id.value, resync)
        val templateWrite = ReadModelWrite.of(readModels, ReadModelKind.ACTIVE_TEMPLATE, id.value, resync)
        val group = if (deleted) null else groups.find(id)
        if (group == null) {
            val changed = readModels.write(ReadModelKind.GROUP, id.value, null, groupWrite)
            readModels.write(ReadModelKind.ACTIVE_TEMPLATE, id.value, null, templateWrite)
            if (changed || !resync) {
                broadcasts.group(
                    GroupUpdated
                        .newBuilder()
                        .setGroupId(id.value)
                        .setDeleted(true)
                        .build(),
                )
            }
            return
        }
        val message = ReadModelMessages.group(group)
        val changed = readModels.write(ReadModelKind.GROUP, id.value, message.toByteArray(), groupWrite)
        // A promote or rollback by the API arrives as a group change.
        templates.publishActive(group.value, templateWrite)
        if (changed || !resync) broadcasts.group(message)
    }

    fun networkSettings(resync: Boolean = false) {
        val stored = readModels.get(ReadModelKind.NETWORK_SETTINGS, NETWORK_SUBJECT)
        val message = ReadModelMessages.networkSettings(network.load())
        val write = if (resync) ReadModelWrite.Replace(stored) else ReadModelWrite.Overwrite
        if (!readModels.write(ReadModelKind.NETWORK_SETTINGS, NETWORK_SUBJECT, message.toByteArray(), write) &&
            resync
        ) {
            return
        }
        broadcasts.network(message)
        val previous = stored?.let(NetworkSettingsUpdated::parseFrom)
        if (previous?.permissionSystemEnabled == false && message.permissionSystemEnabled) resyncOnlinePlayers()
    }

    fun permissionGroup(
        id: String,
        resync: Boolean = false,
    ) {
        val write = ReadModelWrite.of(readModels, ReadModelKind.PERMISSION_GROUP, id, resync)
        val group = permissionGroups.find(id)
        val encoded = group?.let { ReadModelMessages.permissionGroup(it).toByteArray() }
        val changed = readModels.write(ReadModelKind.PERMISSION_GROUP, id, encoded, write)
        if (!changed && resync) return
        broadcasts.permissionGroup(id, group?.revision ?: 0, deleted = group == null)
    }

    /** An announced NPC change: the group's list is rewritten and that one NPC is broadcast. */
    fun npc(
        npcId: String,
        groupId: GroupId,
        deleted: Boolean,
    ) {
        readModels.put(
            ReadModelKind.NPCS_BY_GROUP,
            groupId.value,
            ReadModelMessages.npcs(npcs.findByGroup(groupId)).toByteArray(),
        )
        broadcasts.npc(npcId, groupId.value, deleted)
    }

    /**
     * Rebuilds the config read models (groups, active templates, NPCs, network, permission groups) and the online
     * players' ranks, and broadcasts what differs, e.g. after a lost announcement or a Valkey failover.
     */
    fun resyncAll() {
        val active = groups.findAll().map { it.value.id }
        val activeIds = active.map { it.value }.toSet()
        active.forEach { id ->
            group(id, resync = true)
            resyncNpcs(id)
        }
        (readModels.ids(ReadModelKind.GROUP) - activeIds).forEach { group(GroupId(it), deleted = true, resync = true) }
        // A group restored meanwhile gets its list back on the next resync.
        (
            readModels.ids(
                ReadModelKind.NPCS_BY_GROUP,
            ) - activeIds
        ).forEach { readModels.delete(ReadModelKind.NPCS_BY_GROUP, it) }
        networkSettings(resync = true)
        val permissionGroupIds = permissionGroups.findAll().map { it.value.id }.toSet()
        (
            permissionGroupIds +
                readModels.ids(
                    ReadModelKind.PERMISSION_GROUP,
                )
        ).forEach { permissionGroup(it, resync = true) }
        sessions.onlinePlayers().forEach(permissions::resync)
    }

    private fun resyncNpcs(groupId: GroupId) {
        val stored = readModels.get(ReadModelKind.NPCS_BY_GROUP, groupId.value)
        val current = ReadModelMessages.npcs(npcs.findByGroup(groupId))
        val write = ReadModelWrite.Replace(stored)
        if (!readModels.write(ReadModelKind.NPCS_BY_GROUP, groupId.value, current.toByteArray(), write)) return
        val before =
            stored
                ?.let(ListNpcsResponse::parseFrom)
                ?.npcsList
                .orEmpty()
                .associateBy { it.npcId }
        current.npcsList.filter { before[it.npcId] != it }.forEach {
            broadcasts.npc(
                it.npcId,
                groupId.value,
                deleted = false,
            )
        }
        (before.keys - current.npcsList.map { it.npcId }.toSet()).forEach {
            broadcasts.npc(
                it,
                groupId.value,
                deleted = true,
            )
        }
    }

    /** The permission system was switched back on: every online player's proxy needs their rank again. */
    private fun resyncOnlinePlayers() {
        sessions.onlinePlayers().forEach(permissions::publish)
    }
}
