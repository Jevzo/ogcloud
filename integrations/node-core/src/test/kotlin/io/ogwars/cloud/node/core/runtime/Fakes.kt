package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.contracts.node.v1.AdminRequest
import io.ogwars.cloud.contracts.node.v1.AdminRequestResult
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.core.state.Backend
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** A session the test drives: it records what the node sends and answers commands with [results]. */
class FakeSession(
    val listener: SessionListener,
) : NodeSession {
    val sent = mutableListOf<SessionRequest>()
    val subscriptions = mutableListOf<String>()
    var connected = true
    var started = false

    /** The answer to every server command; null fails it like a dropped session. */
    var result: ServerCommandResult? = ServerCommandResult.getDefaultInstance()
    val commands = mutableListOf<ServerCommandRequest>()
    var adminResult: AdminRequestResult = AdminRequestResult.getDefaultInstance()
    val adminRequests = mutableListOf<AdminRequest>()

    override fun start() {
        started = true
    }

    override fun send(request: SessionRequest): Boolean {
        if (connected) sent += request
        return connected
    }

    override fun serverCommand(command: ServerCommandRequest.Builder): CompletableFuture<ServerCommandResult> {
        commands += command.build()
        return result?.let { CompletableFuture.completedFuture(it) }
            ?: CompletableFuture.failedFuture(IllegalStateException("dropped"))
    }

    override fun adminRequest(request: AdminRequest.Builder): CompletableFuture<AdminRequestResult> {
        adminRequests += request.build()
        return CompletableFuture.completedFuture(adminResult)
    }

    override fun subscribe(channel: String) {
        subscriptions += "+$channel"
    }

    override fun unsubscribe(channel: String) {
        subscriptions += "-$channel"
    }

    override fun close() = Unit
}

/** A scheduler that only records periodic tasks, so tests run them by hand. */
class ManualScheduler {
    val tasks = mutableListOf<Runnable>()
    val executor: ScheduledExecutorService =
        mockk {
            val task = slot<Runnable>()
            every { scheduleAtFixedRate(capture(task), any(), any(), TimeUnit.MILLISECONDS) } answers {
                tasks += task.captured
                mockk<ScheduledFuture<*>>(relaxed = true)
            }
        }

    fun runAll() = tasks.forEach(Runnable::run)
}

/** What both fake platforms record. */
open class FakePlatform : NodePlatform {
    val players = mutableListOf<OnlinePlayer>()
    val granted = mutableMapOf<UUID, MutableSet<String>>()
    val messages = mutableListOf<Pair<UUID, String>>()
    val consoleCommands = mutableListOf<String>()
    val rankChanges = mutableListOf<Set<UUID>>()
    var sample = HeartbeatSample(3, 19.9, 12.5, 512, 1024)

    override fun onlinePlayers() = players.toList()

    override fun sample() = sample

    override fun hasPermission(
        player: UUID,
        node: String,
    ) = node in granted[player].orEmpty()

    override fun message(
        player: UUID,
        text: String,
    ): Boolean {
        if (players.none { it.uuid == player }) return false
        messages += player to text
        return true
    }

    override fun runConsoleCommand(command: String): CompletableFuture<Boolean> {
        consoleCommands += command
        return CompletableFuture.completedFuture(command != "unknown")
    }

    override fun ranksChanged(players: Set<UUID>) {
        rankChanges += players
    }
}

class FakeProxyPlatform :
    FakePlatform(),
    ProxyPlatform {
    val registered = mutableListOf<String>()
    val connects = mutableListOf<Pair<UUID, String>>()
    val kicks = mutableListOf<Pair<UUID, String>>()

    override fun registered(backend: Backend) {
        registered += backend.id
    }

    override fun unregistered(backend: Backend) {
        registered -= backend.id
    }

    override fun connect(
        player: UUID,
        backend: Backend,
    ) {
        connects += player to backend.id
    }

    override fun kick(
        player: UUID,
        text: String,
    ) {
        kicks += player to text
    }
}

class FakeServerPlatform :
    FakePlatform(),
    ServerPlatform {
    val npcUpdates = mutableListOf<NpcUpdated>()

    override fun npcChanged(update: NpcUpdated) {
        npcUpdates += update
    }
}
