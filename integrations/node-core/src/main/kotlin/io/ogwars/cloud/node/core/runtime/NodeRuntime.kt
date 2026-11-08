package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.Heartbeat
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.node.client.DeliveryOutcome
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.core.permission.PermissionCache
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.sdk.LiveChannelHub
import io.ogwars.cloud.node.core.state.NetworkState
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.sdk.OgCloudNode
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * node-core's side of a server or proxy: the gateway session and what it learned (settings, groups, ranks), the
 * heartbeat, deliveries and the SDK. Platform adapters create [ProxyRuntime] or [ServerRuntime] and call [start].
 */
abstract class NodeRuntime(
    val identity: NodeIdentity,
    private val platform: NodePlatform,
    val queries: NodeQueries,
    val messages: Messages,
    private val scheduler: ScheduledExecutorService,
    sessionOf: (SessionListener) -> NodeSession,
) : AutoCloseable {
    protected val log: System.Logger = System.getLogger(javaClass.name)
    val network = NetworkState()
    val permissions = PermissionCache(queries)
    protected val session: NodeSession = sessionOf(Listener())
    internal val live = LiveChannelHub(session)

    /** Completes on the first Welcome; the SDK's onReady waits for it. */
    protected val ready = CompletableFuture<Unit>()
    private val tasks = mutableListOf<ScheduledFuture<*>>()

    /** The SDK for this node's plugins. */
    abstract val api: OgCloudNode

    fun start() {
        session.start()
        every(HEARTBEAT, "heartbeat", ::heartbeat)
        startTasks()
    }

    override fun close() {
        tasks.forEach { it.cancel(false) }
        session.close()
    }

    /** A joined player's rank: the proxy passes the profile its login returned, a backend loads it. */
    protected fun loadRank(
        player: UUID,
        profile: PlayerProfile? = null,
    ) = permissions.load(player, profile).thenAccept { rank -> if (rank != null) platform.ranksChanged(setOf(player)) }

    /** Runs [task] every [interval]; a failure is logged and the next run still happens. */
    protected fun every(
        interval: Duration,
        name: String,
        task: () -> Unit,
    ) {
        val guarded =
            Runnable { runCatching(task).onFailure { log.log(System.Logger.Level.WARNING, "The $name failed", it) } }
        tasks += scheduler.scheduleAtFixedRate(guarded, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)
    }

    protected open fun startTasks() = Unit

    protected abstract fun welcomed(welcome: Welcome)

    protected open fun lifecycle(event: ServerLifecycleChanged) = Unit

    /** [before] is what the node held until now. */
    protected open fun settingsChanged(before: NetworkSettingsUpdated) = platform.settingsChanged(network.settings)

    protected open fun groupChanged(
        update: GroupUpdated,
        wasInMaintenance: Boolean,
    ) = Unit

    protected open fun npcChanged(update: NpcUpdated) = Unit

    /** Deliveries only some node types get: transfers and link codes go to proxies (D-0116). */
    protected open fun deliverOther(delivery: Delivery): CompletableFuture<DeliveryOutcome> =
        CompletableFuture.completedFuture(DeliveryOutcome(false, "not for ${identity.groupType}"))

    private fun reloadRanks() =
        permissions.reloadAll().whenComplete { players, error ->
            if (error != null) {
                log.log(System.Logger.Level.WARNING, "Could not reload the ranks after reconnecting", error)
            } else if (players.isNotEmpty()) {
                platform.ranksChanged(players)
            }
        }

    private fun heartbeat() {
        val sample = platform.sample()
        val heartbeat =
            Heartbeat
                .newBuilder()
                .setPlayers(sample.players)
                .setMaxPlayers(identity.maxPlayers)
                .setTps(sample.tps)
                .setMspt(sample.mspt)
                .setMemoryUsedBytes(sample.memoryUsedBytes)
                .setMemoryMaxBytes(sample.memoryMaxBytes)
        session.send(SessionRequest.newBuilder().setHeartbeat(heartbeat).build())
    }

    private fun broadcast(event: NodeBroadcast) {
        when (event.updateCase) {
            NodeBroadcast.UpdateCase.NETWORK_SETTINGS -> {
                val before = network.settings
                if (network.update(event.networkSettings)) settingsChanged(before)
            }

            NodeBroadcast.UpdateCase.GROUP -> {
                val wasInMaintenance = network.inMaintenance(event.group.groupId)
                if (network.update(event.group)) groupChanged(event.group, wasInMaintenance)
            }

            NodeBroadcast.UpdateCase.PERMISSION_GROUP -> {
                permissions.refresh(event.permissionGroup).thenAccept { if (it.isNotEmpty()) platform.ranksChanged(it) }
            }

            NodeBroadcast.UpdateCase.PLAYER_PERMISSIONS -> {
                val player = UUID.fromString(event.playerPermissions.playerUuid)
                permissions.refresh(event.playerPermissions).thenAccept {
                    if (it !=
                        null
                    ) {
                        platform.ranksChanged(setOf(player))
                    }
                }
            }

            NodeBroadcast.UpdateCase.NPC -> {
                npcChanged(event.npc)
            }

            NodeBroadcast.UpdateCase.UPDATE_NOT_SET, null -> {
                log.log(System.Logger.Level.DEBUG, "Ignoring an empty broadcast about {0}", event.subjectId)
            }
        }
    }

    private fun deliver(delivery: Delivery): CompletableFuture<DeliveryOutcome> =
        when (delivery.actionCase) {
            Delivery.ActionCase.COMMAND -> {
                platform
                    .runConsoleCommand(delivery.command.command)
                    .thenApply { known -> DeliveryOutcome(known, if (known) "" else "unknown command") }
            }

            else -> {
                deliverOther(delivery)
            }
        }

    private inner class Listener : SessionListener {
        override fun welcomed(welcome: Welcome) {
            val before = network.settings
            network.update(welcome.networkSettings)
            this@NodeRuntime.welcomed(welcome)
            settingsChanged(before)
            // A reconnect may have missed rank broadcasts, and the gateway replays none.
            if (!ready.complete(Unit)) reloadRanks()
        }

        override fun lifecycle(event: ServerLifecycleChanged) = this@NodeRuntime.lifecycle(event)

        override fun broadcast(event: NodeBroadcast) = this@NodeRuntime.broadcast(event)

        override fun deliver(delivery: Delivery) = this@NodeRuntime.deliver(delivery)

        override fun liveMessage(message: LiveChannelDelivery) = live.deliver(message)

        override fun disconnected(cause: Throwable?) {
            log.log(System.Logger.Level.INFO, "Lost the gateway session; reconnecting")
        }
    }

    private companion object {
        val HEARTBEAT: Duration = Duration.ofSeconds(5)
    }
}
