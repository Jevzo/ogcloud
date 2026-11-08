package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * The network settings and group flags the gateway sent, from the Welcome and later broadcasts. An update older than
 * what the node holds is ignored, so a late broadcast cannot undo a newer one (D-0077).
 */
class NetworkState {
    private val current = AtomicReference(NetworkSettingsUpdated.getDefaultInstance())
    private val groups = ConcurrentHashMap<String, GroupUpdated>()

    val settings: NetworkSettingsUpdated get() = current.get()

    /** True when [update] replaced the settings. */
    fun update(update: NetworkSettingsUpdated): Boolean {
        var applied = false
        current.updateAndGet { held ->
            applied = update.version >= held.version
            if (applied) update else held
        }
        return applied
    }

    /** True when [update] changed what the node knows about the group; a deletion always applies. */
    fun update(update: GroupUpdated): Boolean {
        if (update.deleted) return groups.remove(update.groupId) != null
        var applied = false
        groups.compute(update.groupId) { _, held ->
            applied = held == null || update.version >= held.version
            if (applied) update else held
        }
        return applied
    }

    /**
     * A (re)opened session starts from the groups the Welcome names; anything learned before it may be stale.
     * Broadcasts after the Welcome carry versions and replace these.
     */
    fun resetGroups(known: Collection<GroupInfo>) {
        groups.clear()
        known.forEach { groups[it.groupId] = unversioned(it) }
    }

    /** Group flags from a query, kept only where no broadcast has said more since the Welcome. */
    fun know(group: GroupInfo) {
        groups.compute(group.groupId) { _, held ->
            if (held == null ||
                held.version == 0L
            ) {
                unversioned(group)
            } else {
                held
            }
        }
    }

    private fun unversioned(group: GroupInfo): GroupUpdated =
        GroupUpdated
            .newBuilder()
            .setGroupId(group.groupId)
            .setGroupType(group.groupType)
            .setMaintenance(group.maintenance)
            .build()

    fun inMaintenance(groupId: String): Boolean = groups[groupId]?.maintenance ?: false

    fun groupType(groupId: String): GroupType? = groups[groupId]?.groupType
}
