package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.PlayerConnected
import io.ogwars.cloud.contracts.events.v1.PlayerDisconnected
import io.ogwars.cloud.contracts.events.v1.PlayerSwitched
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.SnapshotPlayer
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.PlayerActivityReport
import io.ogwars.cloud.contracts.node.v1.PlayerSnapshot
import io.ogwars.cloud.contracts.node.v1.ResolveLoginRequest
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.node.client.DeliveryOutcome
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.core.command.ProxyCommands
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.routing.Destination
import io.ogwars.cloud.node.core.routing.Route
import io.ogwars.cloud.node.core.routing.Router
import io.ogwars.cloud.node.core.sdk.ProxyApi
import io.ogwars.cloud.node.core.state.Backend
import io.ogwars.cloud.node.core.state.ServerDirectory
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.sdk.OgCloudProxy
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/** Whether a joining player may log in, and with what message they are turned away. */
sealed interface LoginDecision {
    data object Allowed : LoginDecision

    data class Denied(
        val message: String,
    ) : LoginDecision
}

/**
 * A Velocity proxy's runtime: the backend directory and routing, logins, player activity and snapshots (D-0130),
 * transfers and link codes, maintenance enforcement and admin notifications, as v1's proxy did.
 */
class ProxyRuntime(
    identity: NodeIdentity,
    private val platform: ProxyPlatform,
    queries: NodeQueries,
    messages: Messages,
    scheduler: ScheduledExecutorService,
    callbacks: Executor,
    clock: Clock,
    sessionOf: (SessionListener) -> NodeSession,
) : NodeRuntime(identity, platform, queries, messages, scheduler, callbacks, sessionOf) {
    val directory = ServerDirectory(platform)
    val router = Router(directory, network)
    override val api: OgCloudProxy = ProxyApi(identity, queries, session, messages, live, ready, callbacks)

    // Players per backend across the network, from the backends' heartbeats (D-0142).
    @Volatile
    private var networkPlayers: Map<String, Int> = emptyMap()

    /** `/ogcloud`, for the adapter to register. */
    val commands = ProxyCommands(messages, queries, session, network, directory, platform::onlinePlayers, clock).tree

    /**
     * Refuses players while this proxy is full (D-0134), else asks the controller and loads the rank. With the
     * permission system off the controller cannot see the bypass, so [bypass] asks the proxy's permission plugin.
     */
    fun login(
        player: UUID,
        name: String,
        protocol: Int,
        remoteAddress: String,
        bypass: () -> Boolean,
    ): CompletableFuture<LoginDecision> {
        if (platform.onlinePlayers().size >= identity.maxPlayers) {
            return CompletableFuture.completedFuture(LoginDecision.Denied(messages.text(Message.KICK_PROXY_FULL)))
        }
        val settings = network.settings
        if (settings.maintenance && !settings.permissionSystemEnabled && !bypass()) {
            return CompletableFuture.completedFuture(LoginDecision.Denied(settings.maintenanceKickMessage))
        }
        val request =
            ResolveLoginRequest
                .newBuilder()
                .setPlayerUuid(player.toString())
                .setPlayerName(name)
                .setProtocol(protocol)
                .setRemoteAddress(remoteAddress)
                .build()
        return queries
            .resolveLogin(request)
            .thenCompose { response ->
                if (!response.allowed) {
                    CompletableFuture.completedFuture(LoginDecision.Denied(response.kickMessage))
                } else {
                    loadRank(player, response.profile).thenApply { LoginDecision.Allowed }
                }
            }.exceptionally { error ->
                log.log(System.Logger.Level.WARNING, "The login of $name ($player) failed", error)
                LoginDecision.Denied(messages.text(Message.KICK_LOGIN_FAILED))
            }
    }

    /** The first backend for a player who just logged in. */
    fun initialServer(player: UUID): Route = router.initial(bypass(player), ::playersOn)

    fun joined(
        player: UUID,
        name: String,
        protocol: Int,
    ) = report(player) { setConnected(PlayerConnected.newBuilder().setPlayerName(name).setProtocol(protocol)) }

    fun switched(
        player: UUID,
        from: String?,
        to: String,
    ) = report(player) { setSwitched(PlayerSwitched.newBuilder().setFromServerId(from.orEmpty()).setToServerId(to)) }

    fun left(player: UUID) {
        permissions.unload(player)
        report(player) { setDisconnected(PlayerDisconnected.getDefaultInstance()) }
    }

    /** A login that ended before it completed, e.g. the player quit while it was checked; it was never reported. */
    fun loginAbandoned(player: UUID) = permissions.unload(player)

    /** Routes a transferred player (admin, API or SDK); maintenance for transfers is enforced here, not by the gateway. */
    fun route(
        player: UUID,
        destination: Destination,
    ): Route = router.route(destination, bypass(player), ::playersOn)

    override fun startTasks() {
        every(SNAPSHOT, "player snapshot", ::snapshot)
        every(PLAYER_COUNTS, "player count refresh", ::refreshPlayerCounts)
    }

    override fun welcomed(welcome: Welcome) {
        network.resetGroups(
            welcome.serversList
                .distinctBy { it.groupId }
                .map {
                    GroupInfo
                        .newBuilder()
                        .setGroupId(it.groupId)
                        .setGroupType(it.groupType)
                        .setMaintenance(it.groupMaintenance)
                        .build()
                },
        )
        directory.reset(welcome.serversList)
        networkPlayers = welcome.serversList.associate { it.serverId to it.players }
        // The Welcome lacks groups without servers; their maintenance flag matters once one starts.
        queries.groups().whenComplete { groups, error ->
            if (error != null) {
                log.log(System.Logger.Level.WARNING, "Could not list the groups after the Welcome", error)
            } else {
                groups.forEach(network::know)
            }
        }
    }

    override fun lifecycle(event: ServerLifecycleChanged) {
        directory.apply(event)
        val state = event.state.name.removePrefix("SERVER_STATE_")
        notifyAdmins(
            messages.text(
                Message.NOTIFY_SERVER,
                "server" to event.displayName,
                "state" to state,
                "group" to event.groupId,
            ),
        )
    }

    override fun settingsChanged(before: NetworkSettingsUpdated) {
        super.settingsChanged(before)
        val now = network.settings
        if (now.maintenance == before.maintenance) return
        notifyAdmins(messages.text(Message.NOTIFY_NETWORK_MAINTENANCE, "status" to status(now.maintenance)))
        if (now.maintenance) {
            platform
                .onlinePlayers()
                .filterNot {
                    bypass(
                        it.uuid,
                    )
                }.forEach { platform.kick(it.uuid, now.maintenanceKickMessage) }
        }
    }

    override fun groupChanged(
        update: GroupUpdated,
        wasInMaintenance: Boolean,
    ) {
        if (update.maintenance == wasInMaintenance) return
        notifyAdmins(
            messages.text(
                Message.NOTIFY_GROUP_MAINTENANCE,
                "group" to update.groupId,
                "status" to status(update.maintenance),
            ),
        )
        if (!update.maintenance) return
        val players = platform.onlinePlayers().filterNot { bypass(it.uuid) }
        if (update.groupId == identity.groupId) {
            players.forEach { platform.kick(it.uuid, messages.text(Message.KICK_GROUP_MAINTENANCE)) }
            return
        }
        val inGroup = directory.ofGroup(update.groupId).map { it.id }.toSet()
        players
            .filter {
                it.serverId in inGroup
            }.forEach { apply(it.uuid, router.route(Destination.Group(update.groupId), false, ::playersOn)) }
    }

    override fun deliverOther(delivery: Delivery): CompletableFuture<DeliveryOutcome> =
        CompletableFuture.completedFuture(
            when (delivery.actionCase) {
                Delivery.ActionCase.TRANSFER -> transfer(delivery.transfer)
                Delivery.ActionCase.LINK_CODE -> linkCode(delivery.linkCode.playerUuid, delivery.linkCode.code)
                else -> DeliveryOutcome(false, "not for a proxy")
            },
        )

    private fun transfer(request: TransferRequested): DeliveryOutcome {
        val players =
            when (request.subjectCase) {
                TransferRequested.SubjectCase.PLAYER_UUID -> {
                    val player = UUID.fromString(request.playerUuid)
                    platform.onlinePlayers().filter { it.uuid == player }
                }

                TransferRequested.SubjectCase.FROM_SERVER_ID -> {
                    platform.onlinePlayers().filter {
                        it.serverId ==
                            request.fromServerId
                    }
                }

                TransferRequested.SubjectCase.SUBJECT_NOT_SET, null -> {
                    return DeliveryOutcome(false, "no player")
                }
            }
        // A player transfer reaches the player's proxy; one who left meanwhile is not here.
        if (players.isEmpty()) {
            return DeliveryOutcome(request.subjectCase == TransferRequested.SubjectCase.FROM_SERVER_ID, "nobody here")
        }
        when (request.destinationCase) {
            TransferRequested.DestinationCase.KICK_MESSAGE -> {
                players.forEach { platform.kick(it.uuid, request.kickMessage) }
            }

            TransferRequested.DestinationCase.SERVER_ID -> {
                val backend = directory[request.serverId] ?: return DeliveryOutcome(false, "unknown server")
                players.forEach { apply(it.uuid, route(it.uuid, Destination.Server(backend))) }
            }

            TransferRequested.DestinationCase.GROUP_ID -> {
                val strategy = request.routingStrategy.takeIf { it != RoutingStrategy.ROUTING_STRATEGY_UNSPECIFIED }
                players.forEach { apply(it.uuid, route(it.uuid, Destination.Group(request.groupId, strategy))) }
            }

            TransferRequested.DestinationCase.DESTINATION_NOT_SET, null -> {
                return DeliveryOutcome(false, "no destination")
            }
        }
        return DeliveryOutcome(true)
    }

    private fun linkCode(
        player: String,
        code: String,
    ): DeliveryOutcome {
        val shown = platform.message(UUID.fromString(player), messages.text(Message.LINK_CODE, "code" to code))
        return DeliveryOutcome(shown, if (shown) "" else "player not here")
    }

    private fun apply(
        player: UUID,
        route: Route,
    ) = when (route) {
        is Route.To -> platform.connect(player, route.backend)
        is Route.Kick -> platform.kick(player, messages.text(route.reason))
    }

    private fun snapshot() {
        val players =
            platform.onlinePlayers().map {
                SnapshotPlayer
                    .newBuilder()
                    .setPlayerUuid(it.uuid.toString())
                    .setPlayerName(it.name)
                    .setServerId(it.serverId.orEmpty())
                    .build()
            }
        session.send(
            SessionRequest.newBuilder().setPlayerSnapshot(PlayerSnapshot.newBuilder().addAllPlayers(players)).build(),
        )
    }

    private fun report(
        player: UUID,
        activity: PlayerActivityReport.Builder.() -> Unit,
    ) {
        val report = PlayerActivityReport.newBuilder().setPlayerUuid(player.toString()).apply(activity)
        session.send(SessionRequest.newBuilder().setPlayerActivity(report).build())
    }

    private fun notifyAdmins(text: String) =
        platform
            .onlinePlayers()
            .filter {
                platform.hasPermission(it.uuid, PermissionNodes.NOTIFY)
            }.forEach { platform.message(it.uuid, text) }

    private fun bypass(player: UUID) = platform.hasPermission(player, PermissionNodes.MAINTENANCE_BYPASS)

    private fun refreshPlayerCounts() {
        queries.servers().whenComplete { servers, error ->
            if (error != null) {
                // Stale counts only make routing less even until the next refresh.
                log.log(System.Logger.Level.DEBUG, "Could not refresh the player counts", error)
            } else {
                networkPlayers = servers.associate { it.serverId to it.players }
            }
        }
    }

    // The network's count lags by a heartbeat, so the players this proxy just sent there count too.
    private fun playersOn(backend: Backend): Int =
        maxOf(platform.onlinePlayers().count { it.serverId == backend.id }, networkPlayers[backend.id] ?: 0)

    private fun status(enabled: Boolean) =
        messages.text(if (enabled) Message.STATUS_ENABLED else Message.STATUS_DISABLED)

    private companion object {
        val SNAPSHOT: Duration = Duration.ofSeconds(60)
        val PLAYER_COUNTS: Duration = Duration.ofSeconds(5)
    }
}
