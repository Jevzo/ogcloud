package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
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

    /** Group flags a proxy learns from the Welcome's server list, before any broadcast about the group. */
    fun know(
        groupId: String,
        type: GroupType,
        maintenance: Boolean,
    ) {
        groups.putIfAbsent(
            groupId,
            GroupUpdated
                .newBuilder()
                .setGroupId(groupId)
                .setGroupType(type)
                .setMaintenance(maintenance)
                .build(),
        )
    }

    fun inMaintenance(groupId: String): Boolean = groups[groupId]?.maintenance ?: false

    fun groupType(groupId: String): GroupType? = groups[groupId]?.groupType
}
