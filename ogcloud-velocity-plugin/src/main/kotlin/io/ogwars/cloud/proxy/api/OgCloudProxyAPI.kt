package io.ogwars.cloud.proxy.api

import io.ogwars.cloud.api.event.ServerReadyEvent
import io.ogwars.cloud.api.model.PermissionGroup
import io.ogwars.cloud.api.model.PlayerInfo
import io.ogwars.cloud.api.model.RunningServer
import io.ogwars.cloud.api.model.ServerInfo
import io.ogwars.cloud.proxy.api.OgCloudProxyAPI.Companion.get
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

private const val PROXY_API_NOT_INITIALIZED_MESSAGE = "OgCloudProxyAPI not initialized"

/**
 * Public API exposed by the Velocity plugin for code running on the proxy.
 *
 * This interface gives proxy-side integrations access to the network's currently known server and
 * player state, along with a small set of control operations that delegate to the OgCloud API.
 * Implementations are registered by the Velocity plugin during startup and can be reached either
 * through the plugin's internal registration flow or the static [get] accessor.
 *
 * Returned values generally reflect the proxy plugin's local, Redis-backed view of the network at
 * the time of the call.
 */
interface OgCloudProxyAPI {

    /**
     * Returns all currently known backend servers.
     */
    fun getServers(): List<RunningServer>

    /**
     * Returns all currently known backend servers for the supplied group.
     *
     * @param group the target group identifier
     */
    fun getServersByGroup(group: String): List<RunningServer>

    /**
     * Looks up a specific backend server by its cloud server id.
     *
     * @param id the target server id
     * @return the current server snapshot, or `null` when the server is unknown
     */
    fun getServer(id: String): RunningServer?

    /**
     * Resolves the backend server a player is currently associated with.
     *
     * @param uuid the target player's unique id
     * @return the current server for that player, or `null` when unavailable
     */
    fun getServerByPlayer(uuid: UUID): RunningServer?

    /**
     * Looks up basic information about a player from the shared online session state.
     *
     * @param uuid the target player's unique id
     * @return the current player information, or `null` when the player is not known online
     */
    fun findPlayer(uuid: UUID): PlayerInfo?

    /**
     * Returns the cached permission group the proxy currently knows for the supplied player.
     *
     * @param uuid the target player's unique id
     * @return the resolved permission group, or `null` when no cache entry exists
     */
    fun getPlayerGroup(uuid: UUID): PermissionGroup?

    /**
     * Requests that OgCloud provide or start a backend server for the requested group.
     *
     * The returned future completes when the request is accepted by the API, not when the server
     * has fully reached the running state.
     *
     * @param group the group that should provide a server
     */
    fun requestServer(group: String): CompletableFuture<ServerInfo>

    /**
     * Requests a transfer of the specified player to a specific target server id.
     *
     * @param uuid the player to move
     * @param serverId the destination server id
     */
    fun transferPlayer(uuid: UUID, serverId: String): CompletableFuture<Void>

    /**
     * Requests a transfer of the specified player to a server in the supplied group.
     *
     * The eventual target server is chosen by the control plane or proxy routing logic.
     *
     * @param uuid the player to move
     * @param group the destination group
     */
    fun transferPlayerToGroup(uuid: UUID, group: String): CompletableFuture<Void>

    /**
     * Registers a listener that will be called when a backend server reaches the ready state.
     *
     * @param listener callback invoked for ready server notifications
     */
    fun onServerReady(listener: Consumer<ServerReadyEvent>)

    companion object {
        @Volatile
        private var instance: OgCloudProxyAPI? = null

        /**
         * Returns the globally registered proxy API instance for this JVM.
         *
         * @throws IllegalStateException if the Velocity plugin has not registered the API yet
         */
        fun get(): OgCloudProxyAPI = instance ?: throw IllegalStateException(PROXY_API_NOT_INITIALIZED_MESSAGE)

        /**
         * Stores the proxy API instance that should be exposed through [get].
         *
         * This is managed by the Velocity plugin during startup.
         */
        fun set(api: OgCloudProxyAPI) {
            instance = api
        }

        /**
         * Clears the globally registered API instance during shutdown.
         */
        fun clear() {
            instance = null
        }
    }
}
