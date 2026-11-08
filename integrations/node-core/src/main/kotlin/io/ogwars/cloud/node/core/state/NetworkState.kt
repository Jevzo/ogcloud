package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * The network settings and group flags the gateway sent, from the Welcome and later broadcasts. An update older than
 * what the node holds is ignored, so a late broadcast cannot undo a newer one (D-0077); a deleted group is kept as a
 * tombstone, so a late update cannot bring it back.
 */
class NetworkState {
    private val current = AtomicReference(NetworkSettingsUpdated.getDefaultInstance())

    // Replaced as a whole, so a reset is never seen half done.
    private val groups = AtomicReference<Map<String, GroupUpdated>>(emptyMap())

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
        var changed = false
        groups.updateAndGet { held ->
            val known = held[update.groupId]
            val next =
                when {
                    update.deleted -> tombstone(update, known)
                    known == null -> update
                    known.deleted -> update.takeIf { it.version > known.version }
                    else -> update.takeIf { it.version >= known.version }
                }
            changed = next != null && (!update.deleted || (known != null && !known.deleted))
            if (next == null) held else held + (update.groupId to next)
        }
        return changed
    }

    /**
     * A (re)opened session starts from the groups the Welcome names; anything learned before it may be stale.
     * Broadcasts after the Welcome carry versions and replace these.
     */
    fun resetGroups(known: Collection<GroupInfo>) {
        groups.set(known.associate { it.groupId to unversioned(it) })
    }

    /** Group flags from a query, kept only where no broadcast has said more since the Welcome. */
    fun know(group: GroupInfo) {
        groups.updateAndGet { held ->
            val known = held[group.groupId]
            if (known == null ||
                (known.version == 0L && !known.deleted)
            ) {
                held + (group.groupId to unversioned(group))
            } else {
                held
            }
        }
    }

    // Only a newer update brings a deleted group back, also when the deletion itself carried no version.
    private fun tombstone(
        deletion: GroupUpdated,
        known: GroupUpdated?,
    ): GroupUpdated = deletion.toBuilder().setVersion(maxOf(deletion.version, known?.version ?: 0)).build()

    private fun unversioned(group: GroupInfo): GroupUpdated =
        GroupUpdated
            .newBuilder()
            .setGroupId(group.groupId)
            .setGroupType(group.groupType)
            .setMaintenance(group.maintenance)
            .build()

    fun inMaintenance(groupId: String): Boolean = live(groupId)?.maintenance ?: false

    fun groupType(groupId: String): GroupType? = live(groupId)?.groupType

    private fun live(groupId: String): GroupUpdated? = groups.get()[groupId]?.takeUnless { it.deleted }
}
