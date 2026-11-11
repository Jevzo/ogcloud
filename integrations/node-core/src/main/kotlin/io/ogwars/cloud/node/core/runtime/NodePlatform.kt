package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.node.core.state.Backend
import io.ogwars.cloud.node.core.state.ServerDirectory
import io.ogwars.cloud.text.RichText
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** A player on this node; a proxy knows each player's backend, which is null until they reach one. */
data class OnlinePlayer(
    val uuid: UUID,
    val name: String,
    val serverId: String?,
)

/** One heartbeat's numbers; proxies report no tick rate. */
data class HeartbeatSample(
    val players: Int,
    val tps: Double,
    val mspt: Double,
    val memoryUsedBytes: Long,
    val memoryMaxBytes: Long,
)

/**
 * What a platform adapter does for node-core. node-core calls it from its own threads; the adapter switches to the
 * server thread wherever its platform needs that.
 */
interface NodePlatform {
    fun onlinePlayers(): List<OnlinePlayer>

    fun sample(): HeartbeatSample

    /** Whether the player holds [node], through whichever permission system is active. */
    fun hasPermission(
        player: UUID,
        node: String,
    ): Boolean

    /** Shows text to an online player; false when the player is not here. */
    fun message(
        player: UUID,
        text: RichText,
    ): Boolean

    /** Runs a console command; completes with whether the platform knew it. */
    fun runConsoleCommand(command: String): CompletableFuture<Boolean>

    /** These players' ranks changed: their permissions, chat and tablist need refreshing. */
    fun ranksChanged(players: Set<UUID>) = Unit

    fun settingsChanged(settings: NetworkSettingsUpdated) = Unit
}

/** A Velocity proxy; it registers the backends the directory finds. */
interface ProxyPlatform :
    NodePlatform,
    ServerDirectory.Listener {
    fun connect(
        player: UUID,
        backend: Backend,
    )

    fun kick(
        player: UUID,
        text: RichText,
    )
}

/** A Paper or PandaSpigot server. */
interface ServerPlatform : NodePlatform {
    /** A managed NPC of this server's group changed (T-0506). */
    fun npcChanged(update: NpcUpdated) = Unit

    /** The gateway session opened, also again after an outage; what the gateway does not replay, NPCs, is reloaded. */
    fun welcomed() = Unit
}
