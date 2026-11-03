package io.ogwars.cloud.sdk

import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * What every server and proxy can do. Lookups ask the gateway and complete off the server thread; switch to the
 * main thread before touching the game.
 */
interface OgCloudNode {
    /** This server's id. */
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

    /** Starts a server of the group and completes with its id; fails with [OgCloudException] when refused. */
    fun requestServer(groupId: String): CompletableFuture<String>

    /** Moves a player to a server; the player's proxy performs it. */
    fun transferPlayer(
        player: UUID,
        serverId: String,
    )

    /** Moves a player into a group, picking the server by [strategy], or the network's when null. */
    fun transferPlayerToGroup(
        player: UUID,
        groupId: String,
        strategy: RoutingStrategy?,
    )

    val liveChannels: LiveChannels

    /** Runs [action] once the gateway welcomed this server, at once if it already has. */
    fun onReady(action: Runnable)
}

/** A Paper or PandaSpigot server. */
interface OgCloudServer : OgCloudNode {
    /** What the server is doing; ENDING moves its players away and drains it. */
    var gameState: GameState

    /** Uploads this server's files as a new template version; completes with its id. */
    fun pushTemplate(): CompletableFuture<String>

    /** Stops this server gracefully. */
    fun stop(): CompletableFuture<Void>

    /** NPCs that exist only while this server runs. */
    val npcs: RuntimeNpcs
}

/** A Velocity proxy. */
interface OgCloudProxy : OgCloudNode

/** Thrown when the network refuses a request; [message] says why. */
class OgCloudException(
    message: String,
) : RuntimeException(message)
