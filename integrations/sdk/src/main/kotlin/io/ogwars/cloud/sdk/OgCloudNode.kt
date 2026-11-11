package io.ogwars.cloud.sdk

import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * What every server and proxy can do. Lookups ask the gateway and complete off the server thread; switch to the
 * game's thread (the main thread, or the region on Folia) before touching the game.
 */
interface OgCloudNode {
    /** This node's id; proxies have one too. */
    val serverId: String
    val groupId: String
    val groupType: GroupType

    /** Every live server of the network. */
    fun servers(): CompletableFuture<List<ServerInfo>>

    fun servers(groupId: String): CompletableFuture<List<ServerInfo>>

    fun server(serverId: String): CompletableFuture<Optional<ServerInfo>>

    /** Where an online player is; empty when offline. */
    fun findPlayer(player: UUID): CompletableFuture<Optional<PlayerLocation>>

    fun findPlayer(name: String): CompletableFuture<Optional<PlayerLocation>>

    /** Starts a server of the group and completes with its id; fails with [OgCloudException] when refused or offline. */
    fun requestServer(groupId: String): CompletableFuture<String>

    /**
     * Moves a player to a server; the player's proxy performs it. Completes once sent, and fails with
     * [OgCloudException] while this node is not connected.
     */
    fun transferPlayer(
        player: UUID,
        serverId: String,
    ): CompletableFuture<Void>

    /** As [transferPlayer], into a group: the server is picked by [strategy], or the network's when null. */
    fun transferPlayerToGroup(
        player: UUID,
        groupId: String,
        strategy: RoutingStrategy?,
    ): CompletableFuture<Void>

    /**
     * Disconnects a player with [message], a tag text: a proxy may kick anyone, a backend only a player on it. Throws
     * IllegalArgumentException for a blank or invalid message; fails like [transferPlayer], or when not allowed.
     */
    fun kickPlayer(
        player: UUID,
        message: String,
    ): CompletableFuture<Void>

    val liveChannels: LiveChannels

    /** Runs [action] off the server thread once the gateway first welcomed this node, or soon if it already has. */
    fun onReady(action: Runnable)
}

/** A Paper or PandaSpigot server. */
interface OgCloudServer : OgCloudNode {
    /** What the server is doing; on DYNAMIC servers, ENDING moves the players away and drains the server. */
    var gameState: GameState

    /**
     * Uploads this server's files as a new template version. Completes once OgCloud accepted the push; the
     * version appears shortly after, when the upload finished.
     */
    fun pushTemplate(): CompletableFuture<Void>

    /** Stops this server gracefully. */
    fun stop(): CompletableFuture<Void>

    /** NPCs that exist only while this server runs. */
    val npcs: RuntimeNpcs
}

/** A Velocity proxy. */
interface OgCloudProxy : OgCloudNode

/** Thrown when the network refuses a request or cannot be reached; [message] says why. */
class OgCloudException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)
