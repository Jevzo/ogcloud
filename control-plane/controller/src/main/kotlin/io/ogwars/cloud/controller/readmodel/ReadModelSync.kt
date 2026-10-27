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
        onlyIfChanged: Boolean = false,
    ) {
        val group = if (deleted) null else groups.find(id)
        if (group == null) {
            if (onlyIfChanged && readModels.get(ReadModelKind.GROUP, id.value) == null) return
            readModels.delete(ReadModelKind.GROUP, id.value)
            readModels.delete(ReadModelKind.ACTIVE_TEMPLATE, id.value)
            broadcasts.group(
                GroupUpdated
                    .newBuilder()
                    .setGroupId(id.value)
                    .setDeleted(true)
                    .build(),
            )
            return
        }
        val message = ReadModelMessages.group(group)
        val changed = store(ReadModelKind.GROUP, id.value, message.toByteArray())
        // A promote or rollback by the API arrives as a group change.
        templates.publishActive(group.value)
        if (changed || !onlyIfChanged) broadcasts.group(message)
    }

    fun networkSettings(onlyIfChanged: Boolean = false) {
        val message = ReadModelMessages.networkSettings(network.load())
        val previous =
            readModels
                .get(
                    ReadModelKind.NETWORK_SETTINGS,
                    NETWORK_SUBJECT,
                )?.let(NetworkSettingsUpdated::parseFrom)
        if (!store(ReadModelKind.NETWORK_SETTINGS, NETWORK_SUBJECT, message.toByteArray()) && onlyIfChanged) return
        broadcasts.network(message)
        if (previous?.permissionSystemEnabled == false && message.permissionSystemEnabled) resyncOnlinePlayers()
    }

    fun permissionGroup(
        id: String,
        onlyIfChanged: Boolean = false,
    ) {
        val group = permissionGroups.find(id)
        if (group == null) {
            if (onlyIfChanged && readModels.get(ReadModelKind.PERMISSION_GROUP, id) == null) return
            readModels.delete(ReadModelKind.PERMISSION_GROUP, id)
            broadcasts.permissionGroup(id, version = 0, deleted = true)
            return
        }
        val changed = store(ReadModelKind.PERMISSION_GROUP, id, ReadModelMessages.permissionGroup(group).toByteArray())
        if (changed || !onlyIfChanged) broadcasts.permissionGroup(id, group.revision, deleted = false)
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

    /** Rebuilds every read model and broadcasts what differs, e.g. after a lost announcement or a Valkey failover. */
    fun resyncAll() {
        val active = groups.findAll().map { it.value.id }
        val activeIds = active.map { it.value }.toSet()
        active.forEach { id ->
            group(id, onlyIfChanged = true)
            resyncNpcs(id)
        }
        (
            readModels.ids(
                ReadModelKind.GROUP,
            ) - activeIds
        ).forEach { group(GroupId(it), deleted = true, onlyIfChanged = true) }
        (
            readModels.ids(
                ReadModelKind.NPCS_BY_GROUP,
            ) - activeIds
        ).forEach { readModels.delete(ReadModelKind.NPCS_BY_GROUP, it) }
        networkSettings(onlyIfChanged = true)
        val permissionGroupIds = permissionGroups.findAll().map { it.value.id }.toSet()
        (permissionGroupIds + readModels.ids(ReadModelKind.PERMISSION_GROUP)).forEach {
            permissionGroup(it, onlyIfChanged = true)
        }
    }

    private fun resyncNpcs(groupId: GroupId) {
        val current = ReadModelMessages.npcs(npcs.findByGroup(groupId))
        val stored = readModels.get(ReadModelKind.NPCS_BY_GROUP, groupId.value)?.let(ListNpcsResponse::parseFrom)
        if (!store(ReadModelKind.NPCS_BY_GROUP, groupId.value, current.toByteArray())) return
        val before = stored?.npcsList.orEmpty().associateBy { it.npcId }
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

    /** Stores [encoded] and tells whether it differed from the stored copy. */
    private fun store(
        kind: ReadModelKind,
        id: String,
        encoded: ByteArray,
    ): Boolean {
        if (readModels.get(kind, id)?.contentEquals(encoded) == true) return false
        readModels.put(kind, id, encoded)
        return true
    }
}
