package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import java.util.concurrent.ConcurrentHashMap

/** A backend a proxy can send players to. */
data class Backend(
    val id: String,
    val displayName: String,
    val groupId: String,
    val groupType: GroupType,
    val host: String,
    val port: Int,
    val maxPlayers: Int,
    /** A draining backend keeps its players but takes no new ones. */
    val draining: Boolean,
)

/**
 * The backends a proxy knows: the Welcome's list, then lifecycle events. RUNNING backends with an address are
 * registered, DRAINING ones stay registered without new players, and stopping ones go.
 */
class ServerDirectory(
    private val listener: Listener,
) {
    /** The proxy registers and unregisters backends with its platform. */
    interface Listener {
        fun registered(backend: Backend)

        fun unregistered(backend: Backend)
    }

    private val backends = ConcurrentHashMap<String, Backend>()

    /** Replaces everything with the Welcome's list; backends missing from it are gone. */
    fun reset(servers: List<ServerInfo>) {
        val welcomed = servers.mapNotNull { it.toBackend() }.associateBy { it.id }
        (backends.keys - welcomed.keys).forEach(::remove)
        welcomed.values.forEach(::put)
    }

    fun apply(event: ServerLifecycleChanged) {
        if (event.groupType == GroupType.GROUP_TYPE_PROXY) return
        when (event.state) {
            ServerState.SERVER_STATE_RUNNING, ServerState.SERVER_STATE_DRAINING -> event.toBackend()?.let(::put)
            ServerState.SERVER_STATE_STOPPING, ServerState.SERVER_STATE_STOPPED -> remove(event.serverId)
            else -> Unit
        }
    }

    operator fun get(id: String): Backend? = backends[id]

    fun byDisplayName(name: String): Backend? =
        backends.values.firstOrNull {
            it.displayName.equals(name, ignoreCase = true)
        }

    /** A backend by id or display name, as admins type them. */
    fun find(nameOrId: String): Backend? = get(nameOrId) ?: byDisplayName(nameOrId)

    fun ofGroup(groupId: String): List<Backend> = backends.values.filter { it.groupId == groupId }

    fun all(): List<Backend> = backends.values.toList()

    private fun put(backend: Backend) {
        val previous = backends.put(backend.id, backend)
        // Only a new address needs registering again; a drain flag is the directory's own business.
        if (previous == null || previous.host != backend.host || previous.port != backend.port) {
            listener.registered(backend)
        }
    }

    private fun remove(id: String) {
        backends.remove(id)?.let(listener::unregistered)
    }

    private fun ServerInfo.toBackend(): Backend? =
        backendOf(serverId, displayName, groupId, groupType, host, port, maxPlayers, state)

    private fun ServerLifecycleChanged.toBackend(): Backend? =
        backendOf(serverId, displayName, groupId, groupType, host, port, maxPlayers, state)

    private fun backendOf(
        id: String,
        displayName: String,
        groupId: String,
        groupType: GroupType,
        host: String,
        port: Int,
        maxPlayers: Int,
        state: ServerState,
    ): Backend? {
        val routable = state == ServerState.SERVER_STATE_RUNNING || state == ServerState.SERVER_STATE_DRAINING
        if (!routable || host.isEmpty() || groupType == GroupType.GROUP_TYPE_PROXY) return null
        return Backend(
            id,
            displayName,
            groupId,
            groupType,
            host,
            port,
            maxPlayers,
            state == ServerState.SERVER_STATE_DRAINING,
        )
    }
}
