package io.ogwars.cloud.server.api

import io.ogwars.cloud.common.channel.LiveChannelPayload
import io.ogwars.cloud.common.channel.LiveChannelSubscription
import io.ogwars.cloud.common.event.ServerReadyEvent
import io.ogwars.cloud.common.model.*
import io.ogwars.cloud.server.api.OgCloudServerAPI.Companion.get
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

private const val SERVER_API_NOT_INITIALIZED_MESSAGE = "OgCloudServerAPI not initialized"

/**
 * Public entry point exposed by the Paper plugin for code running inside a managed game server.
 *
 * This API provides read access to the current cloud state that is relevant to a Paper server
 * instance, as well as a small set of control operations that delegate back into the OgCloud
 * control plane. Implementations are provided by the plugin during startup and are intended to be
 * consumed by other plugins through Bukkit's service manager or the static [get] accessor.
 *
 * Unless otherwise stated, all methods return data from the plugin's current in-memory or Redis
 * backed view of the network and do not perform heavy blocking work on the main thread.
 */
interface OgCloudServerAPI {
    /**
     * Returns the unique cloud server identifier assigned to this Paper instance.
     *
     * This value is stable for the lifetime of the running server process and matches the server id
     * used in OgCloud events, Redis records, and controller APIs.
     */
    fun getServerId(): String

    /**
     * Returns the logical group name this server belongs to.
     *
     * The group is the cloud-level grouping used for routing, scaling, and maintenance decisions.
     */
    fun getGroupName(): String

    /**
     * Returns the configured [GroupType] for this server's group.
     */
    fun getGroupType(): GroupType

    /**
     * Returns the current game state last published by this server through the plugin.
     *
     * This is the local state held by the plugin and may be used by other plugins to coordinate
     * gameplay flow or availability rules.
     */
    fun getGameState(): GameState

    /**
     * Updates the local game state and publishes the change to the cloud event stream.
     *
     * Implementations are expected to broadcast the new state to interested infrastructure
     * components so that proxies, dashboards, and automation can react to it.
     *
     * @param state the new game state to publish
     */
    fun setGameState(state: GameState)

    /**
     * Returns all currently known non-proxy servers in the network.
     *
     * The result is derived from the plugin's current Redis-backed view and may omit servers that
     * are not yet registered or have not published state recently.
     */
    fun getServers(): List<RunningServer>

    /**
     * Returns all currently known servers for the supplied group.
     *
     * @param group the cloud group identifier to filter by
     */
    fun getServersByGroup(group: String): List<RunningServer>

    /**
     * Looks up a specific running server by its cloud server id.
     *
     * @param id the target server id
     * @return the running server snapshot, or `null` when the server is not currently known
     */
    fun getServer(id: String): RunningServer?

    /**
     * Resolves the server a player is currently connected to.
     *
     * The player lookup is based on the shared Redis player session maintained by the network.
     *
     * @param uuid the target player's unique id
     * @return the current running server for that player, or `null` when unavailable
     */
    fun getServerByPlayer(uuid: UUID): RunningServer?

    /**
     * Looks up basic information about an online player from the shared network session state.
     *
     * @param uuid the target player's unique id
     * @return player session information, or `null` when the player is not known online
     */
    fun findPlayer(uuid: UUID): PlayerInfo?

    /**
     * Returns the cached permission group currently associated with the given player on this
     * server.
     *
     * This reflects the permission data known to the Paper plugin, not necessarily a fresh
     * database read.
     *
     * @param uuid the target player's unique id
     * @return the resolved permission group, or `null` when the player is not cached
     */
    fun getPlayerGroup(uuid: UUID): PermissionGroup?

    /**
     * Requests that OgCloud provide or start a server in the requested group.
     *
     * The returned future completes when the API accepts the request, not when the new server is
     * fully online.
     *
     * @param group the group that should provide a server
     * @return a future containing the accepted target server metadata
     */
    fun requestServer(group: String): CompletableFuture<ServerInfo>

    /**
     * Requests that a player be transferred to a specific target server id.
     *
     * The transfer is asynchronous and handled by the network proxy layer.
     *
     * @param uuid the player to move
     * @param serverId the destination server id
     */
    fun transferPlayer(
        uuid: UUID,
        serverId: String,
    ): CompletableFuture<Void>

    /**
     * Requests that a player be transferred to a server in the supplied group.
     *
     * Group-based transfers allow the control plane or proxy to pick an appropriate destination.
     *
     * @param uuid the player to move
     * @param group the destination group
     */
    fun transferPlayerToGroup(
        uuid: UUID,
        group: String,
    ): CompletableFuture<Void>

    /**
     * Requests an immediate template push for the current server instance.
     *
     * This is primarily intended for operational tooling or development workflows where the
     * running server should refresh its template assets.
     */
    fun forceTemplatePush(): CompletableFuture<Void>

    /**
     * Subscribes to a named live channel using the supplied payload type.
     *
     * Messages are delivered only while this server is online and only to local listeners that
     * explicitly subscribed to the same channel. Listener callbacks run on an OgCloud-managed
     * background thread, so Bukkit work should be handed off to the main thread when needed.
     *
     * @param channelName the logical live channel name
     * @param payloadType the concrete payload class expected on this channel
     * @param listener callback invoked for matching live payloads
     * @return a registration handle that can be used to unsubscribe later
     */
    fun <T : LiveChannelPayload> subscribe(
        channelName: String,
        payloadType: Class<T>,
        listener: Consumer<T>,
    ): LiveChannelSubscription

    /**
     * Publishes a typed payload object onto the supplied live channel.
     *
     * The payload is serialized internally by OgCloud and distributed as live-only traffic across
     * currently running Paper and Velocity nodes.
     *
     * @param channelName the logical live channel name
     * @param payload the concrete payload object to publish
     */
    fun <T : LiveChannelPayload> publish(
        channelName: String,
        payload: T,
    )

    /**
     * Registers a listener that will be notified when another backend server becomes ready.
     *
     * Listeners are invoked on the plugin-managed thread that processes lifecycle events, so
     * consumers should avoid blocking work inside the callback.
     *
     * @param listener callback invoked for ready server events
     */
    fun onServerReady(listener: Consumer<ServerReadyEvent>)

    /**
     * Creates a builder for a runtime-only NPC that exists only on this Paper server.
     *
     * Runtime NPCs are not persisted in the control plane and are not replicated to other
     * backend servers. They are intended for local plugin integrations that need NPC rendering
     * and click callbacks without going through the REST-backed managed NPC path.
     *
     * @param id the local runtime NPC identifier, unique on this server
     */
    fun runtimeNpc(id: String): OgCloudRuntimeNpcBuilder

    /**
     * Resolves a previously spawned runtime NPC handle by id.
     *
     * @param id the runtime NPC identifier
     * @return the active runtime NPC handle, or `null` when not found or already despawned
     */
    fun findRuntimeNpc(id: String): OgCloudRuntimeNpcHandle?

    companion object {
        @Volatile
        private var instance: OgCloudServerAPI? = null

        /**
         * Returns the globally registered API instance for the current JVM.
         *
         * @throws IllegalStateException if the Paper plugin has not finished registering the API
         */
        fun get(): OgCloudServerAPI = instance ?: throw IllegalStateException(SERVER_API_NOT_INITIALIZED_MESSAGE)

        /**
         * Stores the API instance that should be exposed through [get].
         *
         * This is called by the Paper plugin during startup and is not intended for general plugin
         * code to override.
         */
        fun set(api: OgCloudServerAPI) {
            instance = api
        }

        /**
         * Clears the globally registered API instance.
         *
         * This is used during plugin shutdown so stale references are not retained after disable.
         */
        fun clear() {
            instance = null
        }
    }
}
