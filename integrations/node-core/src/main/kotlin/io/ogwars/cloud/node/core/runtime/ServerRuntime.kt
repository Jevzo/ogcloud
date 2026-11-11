package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.contracts.node.v1.NpcChangeRequest
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.sdk.ServerApi
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.sdk.OgCloudServer
import io.ogwars.cloud.sdk.RuntimeNpcs
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/** A Paper or PandaSpigot server's runtime: ranks of the players here, game state, and NPC updates. */
class ServerRuntime(
    identity: NodeIdentity,
    private val platform: ServerPlatform,
    queries: NodeQueries,
    messages: Messages,
    texts: StoredTexts,
    scheduler: ScheduledExecutorService,
    callbacks: Executor,
    npcs: RuntimeNpcs,
    sessionOf: (SessionListener) -> NodeSession,
) : NodeRuntime(identity, platform, queries, messages, texts, scheduler, callbacks, sessionOf) {
    private val server =
        ServerApi(identity, queries, session, messages, live, ready, callbacks, npcs) { player ->
            platform.onlinePlayers().any { it.uuid == player }
        }
    override val api: OgCloudServer = server

    fun joined(player: UUID) = loadRank(player)

    fun left(player: UUID) = permissions.unload(player)

    /** Sends an in-game admin's NPC change for the API (D-0129); false while the session is down. */
    fun requestNpcChange(change: NpcChangeRequest.Builder): Boolean =
        session.send(SessionRequest.newBuilder().setNpcChange(change).build())

    override fun welcomed(welcome: Welcome) {
        server.announceGameState()
        platform.welcomed()
    }

    override fun npcChanged(update: NpcUpdated) = platform.npcChanged(update)
}
