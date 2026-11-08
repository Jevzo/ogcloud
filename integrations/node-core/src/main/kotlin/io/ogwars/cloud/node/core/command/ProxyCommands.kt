package io.ogwars.cloud.node.core.command

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.RankDurations
import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.NodeCommand
import io.ogwars.cloud.contracts.events.v1.PushTemplate
import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.StopServer
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.contracts.node.v1.AdminRequest
import io.ogwars.cloud.contracts.node.v1.GroupMaintenanceChange
import io.ogwars.cloud.contracts.node.v1.NetworkMaintenanceChange
import io.ogwars.cloud.contracts.node.v1.NodeCommandRequest
import io.ogwars.cloud.contracts.node.v1.RankChange
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.toInstant
import io.ogwars.cloud.contracts.toProtoDuration
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.state.NetworkState
import io.ogwars.cloud.node.core.state.ServerDirectory
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.reason
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * The proxy's `/ogcloud` command with every v1 subcommand (D-0132). Reads ask the gateway; gateway-checked changes
 * carry the acting player (D-0115, D-0117, D-0132), so only players run them. Transfers are checked here only.
 */
class ProxyCommands(
    private val messages: Messages,
    private val queries: NodeQueries,
    private val session: NodeSession,
    private val network: NetworkState,
    private val directory: ServerDirectory,
    private val onlinePlayers: () -> List<OnlinePlayer>,
    private val clock: Clock,
) {
    private val log = System.getLogger(ProxyCommands::class.java.name)
    private val servers = Argument("server", suggestions = { directory.all().map { it.displayName } })
    private val players = Argument("player", suggestions = { onlinePlayers().map { it.name } })

    // Built last: it reads the shared arguments above.
    val tree =
        CommandTree(messages, server() + group() + player() + network() + permissions() + console(), Message.INFO)

    private fun server() =
        listOf(
            Subcommand(
                listOf("server", "list"),
                listOf(Argument("group", optional = true)),
                PermissionNodes.ADMIN_SERVER,
            ) {
                listServers(it)
            },
            Subcommand(listOf("server", "info"), listOf(servers), PermissionNodes.ADMIN_SERVER) { serverInfo(it) },
            Subcommand(
                listOf("server", "request"),
                listOf(Argument("group")),
                PermissionNodes.ADMIN_SERVER,
                true,
            ) { call ->
                val group = call.word("group")
                command(
                    call,
                    ServerCommandRequest.newBuilder().setRequest(RequestServer.newBuilder().setGroupId(group)),
                ) {
                    reply(call, Message.SERVER_REQUESTED, "server" to it.serverId, "group" to group)
                }
            },
            serverCommand("stop", Message.SERVER_STOPPING) { setStop(StopServer.newBuilder().setReason(ADMIN_REASON)) },
            serverCommand("kill", Message.SERVER_KILLED) { setKill(KillServer.newBuilder().setReason(ADMIN_REASON)) },
            serverCommand(
                "templatepush",
                Message.SERVER_PUSHING,
            ) { setPushTemplate(PushTemplate.getDefaultInstance()) },
        )

    private fun group() =
        listOf(
            Subcommand(listOf("group", "list"), emptyList(), PermissionNodes.ADMIN_GROUP) { listGroups(it) },
            Subcommand(
                listOf("group", "info"),
                listOf(Argument("group")),
                PermissionNodes.ADMIN_GROUP,
            ) { groupInfo(it) },
            Subcommand(
                listOf("group", "maintenance"),
                listOf(Argument("group"), Argument("enabled", Argument.Kind.BOOLEAN)),
                PermissionNodes.ADMIN_GROUP,
                playersOnly = true,
            ) { call ->
                val group = call.word("group")
                val enabled = call.bool("enabled")
                admin(
                    call,
                    AdminRequest.newBuilder().setGroupMaintenance(
                        GroupMaintenanceChange.newBuilder().setGroupId(group).setEnabled(enabled),
                    ),
                ) {
                    reply(call, Message.GROUP_MAINTENANCE_SET, "group" to group, "status" to status(enabled))
                }
            },
        )

    private fun player() =
        listOf(
            Subcommand(listOf("player", "find"), listOf(players), PermissionNodes.ADMIN_PLAYER) { findPlayer(it) },
            Subcommand(listOf("player", "list"), listOf(servers), PermissionNodes.ADMIN_PLAYER) { listPlayers(it) },
            Subcommand(
                listOf("player", "transfer"),
                listOf(players, Argument("target")),
                PermissionNodes.ADMIN_PLAYER,
            ) {
                transfer(it)
            },
        )

    private fun network() =
        listOf(
            Subcommand(listOf("network", "info"), emptyList(), PermissionNodes.ADMIN_NETWORK) { networkInfo(it) },
            Subcommand(
                listOf("network", "maintenance"),
                listOf(Argument("enabled", Argument.Kind.BOOLEAN)),
                PermissionNodes.ADMIN_NETWORK,
                playersOnly = true,
            ) { call ->
                val enabled = call.bool("enabled")
                admin(
                    call,
                    AdminRequest.newBuilder().setNetworkMaintenance(
                        NetworkMaintenanceChange.newBuilder().setEnabled(enabled),
                    ),
                ) {
                    reply(call, Message.NETWORK_MAINTENANCE_SET, "status" to status(enabled))
                }
            },
        )

    private fun permissions() =
        listOf(
            Subcommand(
                listOf("perm", "group", "list"),
                emptyList(),
                PermissionNodes.ADMIN_PERMISSION,
            ) { listPermissionGroups(it) },
            Subcommand(listOf("perm", "group", "info"), listOf(Argument("group")), PermissionNodes.ADMIN_PERMISSION) {
                permissionGroupInfo(it)
            },
            Subcommand(
                listOf("perm", "player", "get"),
                listOf(players),
                PermissionNodes.ADMIN_PERMISSION,
            ) { rankOf(it) },
            Subcommand(
                listOf("perm", "player", "set"),
                listOf(players, Argument("group"), Argument("duration", Argument.Kind.REST)),
                PermissionNodes.ADMIN_PERMISSION,
                playersOnly = true,
            ) { setRank(it) },
        )

    private fun console() =
        listOf(
            consoleCommand("all", null) { setAll(true) },
            consoleCommand("server", servers) { setServerId(it) },
            consoleCommand("group", Argument("group")) { setGroupId(it) },
        )

    private fun serverCommand(
        name: String,
        done: Message,
        command: ServerCommandRequest.Builder.() -> Unit,
    ) = Subcommand(listOf("server", name), listOf(servers), PermissionNodes.ADMIN_SERVER, playersOnly = true) { call ->
        resolveServer(call, call.word("server")) { server ->
            command(call, ServerCommandRequest.newBuilder().setServerId(server.serverId).apply(command)) {
                reply(call, done, "server" to server.displayName)
            }
        }
    }

    /** `command all <command>`, `command server <server> <command>` or `command group <group> <command>`. */
    private fun consoleCommand(
        kind: String,
        target: Argument?,
        setTarget: NodeCommand.Builder.(String) -> Unit,
    ) = Subcommand(
        listOf("command", kind),
        listOfNotNull(target, Argument("command", Argument.Kind.REST)),
        PermissionNodes.ADMIN_COMMAND,
        playersOnly = true,
    ) { call ->
        val text = call.word("command")
        val targetName = target?.let { call.word(it.name) }
        val command = NodeCommand.newBuilder().setCommand(text).apply { setTarget(targetName.orEmpty()) }
        val request = NodeCommandRequest.newBuilder().setActingPlayerUuid(call.player.toString()).setCommand(command)
        sendOrFail(call, SessionRequest.newBuilder().setNodeCommand(request).build()) {
            reply(call, Message.COMMAND_SENT, "command" to text, "target" to (targetName ?: kind))
        }
    }

    private fun listServers(call: Invocation) =
        answer(call, queries.servers(call.optional("group"))) { servers ->
            if (servers.isEmpty()) return@answer reply(call, Message.SERVER_LIST_EMPTY)
            reply(call, Message.SERVER_LIST_HEADER, "count" to servers.size)
            servers.sortedBy { it.displayName }.forEach {
                reply(
                    call,
                    Message.SERVER_LIST_ENTRY,
                    "server" to it.displayName,
                    "state" to it.state.name.removePrefix("SERVER_STATE_"),
                    "players" to it.players,
                    "max" to it.maxPlayers,
                )
            }
        }

    private fun serverInfo(call: Invocation) =
        resolveServer(call, call.word("server")) { server ->
            reply(call, Message.SERVER_INFO_HEADER, "server" to server.displayName)
            reply(call, Message.SERVER_INFO_ID, "id" to server.serverId)
            reply(call, Message.SERVER_INFO_GROUP, "group" to server.groupId)
            reply(call, Message.SERVER_INFO_STATE, "state" to server.state.name.removePrefix("SERVER_STATE_"))
            reply(
                call,
                Message.SERVER_INFO_GAME_STATE,
                "game_state" to server.gameState.name.removePrefix("GAME_STATE_"),
            )
            reply(call, Message.SERVER_INFO_PLAYERS, "players" to server.players, "max" to server.maxPlayers)
            reply(call, Message.SERVER_INFO_ADDRESS, "host" to server.host, "port" to server.port)
        }

    private fun listGroups(call: Invocation) =
        answer(call, queries.groups()) { groups ->
            if (groups.isEmpty()) return@answer reply(call, Message.GROUP_LIST_EMPTY)
            reply(call, Message.GROUP_LIST_HEADER, "count" to groups.size)
            groups.forEach {
                val marker = if (it.maintenance) messages.text(Message.GROUP_LIST_MAINTENANCE) else ""
                reply(
                    call,
                    Message.GROUP_LIST_ENTRY,
                    "group" to it.groupId,
                    "type" to it.groupType.name.removePrefix("GROUP_TYPE_"),
                    "maintenance" to marker,
                    "min" to it.minOnline,
                    "max" to it.maxInstances,
                )
            }
        }

    private fun groupInfo(call: Invocation) =
        answer(call, queries.groups()) { groups ->
            val group = groups.firstOrNull { it.groupId == call.word("group") }
            if (group == null) return@answer reply(call, Message.GROUP_NOT_FOUND, "group" to call.word("group"))
            reply(call, Message.GROUP_INFO_HEADER, "group" to group.groupId)
            reply(call, Message.GROUP_INFO_TYPE, "type" to group.groupType.name.removePrefix("GROUP_TYPE_"))
            reply(call, Message.GROUP_INFO_PROFILE, "profile" to group.profileId)
            reply(call, Message.GROUP_INFO_MAINTENANCE, "maintenance" to yesNo(group.maintenance))
            reply(call, Message.GROUP_INFO_INSTANCES, "min" to group.minOnline, "max" to group.maxInstances)
            reply(call, Message.GROUP_INFO_PLAYERS_PER_SERVER, "players" to group.playersPerServer)
        }

    private fun findPlayer(call: Invocation) =
        answer(
            call,
            queries.findPlayer(call.word("player")).thenCombine(names()) { location, names -> location to names },
        ) {
            val (location, names) = it
            if (location == null) return@answer reply(call, Message.PLAYER_NOT_FOUND, "player" to call.word("player"))
            reply(call, Message.PLAYER_FIND_HEADER, "player" to location.playerName)
            reply(call, Message.PLAYER_FIND_UUID, "uuid" to location.playerUuid)
            reply(call, Message.PLAYER_FIND_SERVER, "server" to nameOf(location.serverId, names))
            reply(call, Message.PLAYER_FIND_PROXY, "proxy" to nameOf(location.proxyId, names))
        }

    private fun listPlayers(call: Invocation) =
        resolveServer(call, call.word("server")) { server ->
            answer(call, queries.players(server.serverId).thenCombine(names()) { players, names -> players to names }) {
                val (players, names) = it
                if (players.isEmpty()) {
                    return@answer reply(
                        call,
                        Message.PLAYER_LIST_EMPTY,
                        "server" to server.displayName,
                    )
                }
                reply(call, Message.PLAYER_LIST_HEADER, "server" to server.displayName, "count" to players.size)
                players.sortedBy { player -> player.playerName.lowercase() }.forEach { player ->
                    reply(
                        call,
                        Message.PLAYER_LIST_ENTRY,
                        "player" to player.playerName,
                        "server" to nameOf(player.serverId, names),
                    )
                }
            }
        }

    /** Moves a player to a server, by id or name, or else into a group. */
    private fun transfer(call: Invocation) {
        val target = call.word("target")
        answer(
            call,
            queries.findPlayer(call.word("player")).thenCombine(queries.servers()) { location, servers ->
                location to
                    servers
            },
        ) {
            val (location, servers) = it
            if (location == null) return@answer reply(call, Message.PLAYER_NOT_FOUND, "player" to call.word("player"))
            answer(call, queries.groups()) { groups ->
                val server =
                    servers.firstOrNull { s ->
                        s.serverId == target ||
                            s.displayName.equals(target, ignoreCase = true)
                    }
                val request = TransferRequested.newBuilder().setPlayerUuid(location.playerUuid)
                when {
                    server != null -> request.setServerId(server.serverId)
                    groups.any { g -> g.groupId == target } -> request.setGroupId(target)
                    else -> return@answer reply(call, Message.TARGET_NOT_FOUND, "target" to target)
                }
                sendOrFail(call, SessionRequest.newBuilder().setTransfer(request).build()) {
                    reply(call, Message.PLAYER_TRANSFERRING, "player" to location.playerName, "target" to target)
                }
            }
        }
    }

    private fun networkInfo(call: Invocation) {
        val settings = network.settings
        reply(call, Message.NETWORK_INFO_HEADER)
        reply(call, Message.NETWORK_INFO_MAINTENANCE, "maintenance" to yesNo(settings.maintenance))
        reply(call, Message.NETWORK_INFO_MAX_PLAYERS, "max" to settings.maxPlayers)
        reply(call, Message.NETWORK_INFO_DEFAULT_GROUP, "group" to settings.defaultGroupId)
        reply(call, Message.NETWORK_INFO_MOTD, "motd" to settings.motd)
        reply(call, Message.NETWORK_INFO_VERSION, "version" to settings.versionName)
    }

    private fun listPermissionGroups(call: Invocation) =
        answer(call, queries.permissionGroups()) { groups ->
            if (groups.isEmpty()) return@answer reply(call, Message.PERM_GROUP_LIST_EMPTY)
            reply(call, Message.PERM_GROUP_LIST_HEADER, "count" to groups.size)
            groups.sortedByDescending { it.weight }.forEach {
                val marker = if (it.isDefault) messages.text(Message.PERM_GROUP_LIST_DEFAULT) else ""
                reply(
                    call,
                    Message.PERM_GROUP_LIST_ENTRY,
                    "group" to it.permissionGroupId,
                    "name" to it.name,
                    "weight" to it.weight,
                    "default" to marker,
                    "count" to it.permissionsCount,
                )
            }
        }

    private fun permissionGroupInfo(call: Invocation) =
        answer(call, queries.permissionGroup(call.word("group"))) { group ->
            if (group == null) return@answer reply(call, Message.PERM_GROUP_NOT_FOUND, "group" to call.word("group"))
            reply(call, Message.PERM_GROUP_INFO_HEADER, "group" to group.permissionGroupId)
            reply(call, Message.PERM_GROUP_INFO_NAME, "name" to group.name)
            reply(call, Message.PERM_GROUP_INFO_WEIGHT, "weight" to group.weight)
            reply(call, Message.PERM_GROUP_INFO_DEFAULT, "default" to yesNo(group.isDefault))
            reply(call, Message.PERM_GROUP_INFO_PREFIX, "prefix" to group.chatPrefix)
            reply(call, Message.PERM_GROUP_INFO_SUFFIX, "suffix" to group.chatSuffix)
            reply(call, Message.PERM_GROUP_INFO_NODES, "count" to group.permissionsCount)
            group.permissionsList.sorted().forEach { reply(call, Message.PERM_GROUP_INFO_NODE, "node" to it) }
        }

    private fun rankOf(call: Invocation) =
        answer(call, queries.findPlayer(call.word("player"))) { location ->
            if (location == null) return@answer reply(call, Message.PLAYER_NOT_FOUND, "player" to call.word("player"))
            answer(call, queries.profile(UUID.fromString(location.playerUuid))) { profile ->
                reply(call, Message.PERM_PLAYER_HEADER, "player" to location.playerName)
                reply(call, Message.PERM_PLAYER_GROUP, "group" to profile?.permissionGroupId.orEmpty())
                if (profile == null ||
                    !profile.hasRankExpiresAt()
                ) {
                    return@answer reply(call, Message.PERM_PLAYER_PERMANENT)
                }
                val left =
                    Duration
                        .between(
                            clock.instant(),
                            profile.rankExpiresAt.toInstant(),
                        ).coerceAtLeast(Duration.ZERO)
                reply(call, Message.PERM_PLAYER_EXPIRES, "remaining" to RankDurations.format(left))
            }
        }

    private fun setRank(call: Invocation) {
        val text = call.word("duration")
        val duration =
            runCatching { RankDurations.parse(text) }.getOrElse {
                return reply(call, Message.PERM_DURATION_INVALID, "duration" to text)
            }
        answer(call, queries.findPlayer(call.word("player"))) { location ->
            if (location == null) return@answer reply(call, Message.PLAYER_NOT_FOUND, "player" to call.word("player"))
            val group = call.word("group")
            val rank =
                RankChange
                    .newBuilder()
                    .setPlayerUuid(location.playerUuid)
                    .setPermissionGroupId(group)
                    .apply { duration?.let { setDuration(it.toProtoDuration()) } }
            admin(call, AdminRequest.newBuilder().setRank(rank)) {
                reply(
                    call,
                    Message.PERM_PLAYER_SET,
                    "player" to location.playerName,
                    "group" to group,
                    "duration" to RankDurations.format(duration),
                )
            }
        }
    }

    private fun resolveServer(
        call: Invocation,
        input: String,
        found: (ServerInfo) -> Unit,
    ) = answer(call, queries.servers()) { servers ->
        val server = servers.firstOrNull { it.serverId == input || it.displayName.equals(input, ignoreCase = true) }
        if (server == null) reply(call, Message.SERVER_NOT_FOUND, "server" to input) else found(server)
    }

    /** Ids to display names of every live server, for listings that show where players are. */
    private fun names(): CompletableFuture<Map<String, String>> =
        queries.servers().thenApply { servers -> servers.associate { it.serverId to it.displayName } }

    private fun nameOf(
        id: String,
        names: Map<String, String>,
    ) = if (id.isEmpty()) messages.text(Message.NONE) else names[id] ?: id

    private fun command(
        call: Invocation,
        request: ServerCommandRequest.Builder,
        done: (ServerCommandResult) -> Unit,
    ) = answer(call, session.serverCommand(request.setActingPlayerUuid(call.player.toString()))) {
        if (it.error.isEmpty()) done(it) else failed(call, messages.reason(it.error))
    }

    private fun admin(
        call: Invocation,
        request: AdminRequest.Builder,
        done: () -> Unit,
    ) = answer(call, session.adminRequest(request.setActingPlayerUuid(call.player.toString()))) {
        if (it.error.isEmpty()) done() else failed(call, messages.reason(it.error))
    }

    private fun sendOrFail(
        call: Invocation,
        request: SessionRequest,
        done: () -> Unit,
    ) = if (session.send(request)) done() else failed(call, messages.text(Message.ERROR_NOT_CONNECTED))

    /** Runs [then] with the answer; a failed query tells the sender instead. */
    private fun <T> answer(
        call: Invocation,
        future: CompletableFuture<T>,
        then: (T) -> Unit,
    ) {
        future.whenComplete { value, error ->
            val failure = error ?: runCatching { then(value) }.exceptionOrNull()
            if (failure != null) {
                log.log(System.Logger.Level.WARNING, "An /ogcloud command failed", failure)
                failed(call, messages.text(Message.ERROR_UNAVAILABLE))
            }
        }
    }

    private fun failed(
        call: Invocation,
        error: String,
    ) = reply(call, Message.FAILED, "error" to error)

    private fun reply(
        call: Invocation,
        message: Message,
        vararg values: Pair<String, Any?>,
    ) = call.sender.say(messages, message, *values)

    private fun status(enabled: Boolean) =
        messages.text(if (enabled) Message.STATUS_ENABLED else Message.STATUS_DISABLED)

    private fun yesNo(value: Boolean) = messages.text(if (value) Message.YES else Message.NO)

    private companion object {
        const val ADMIN_REASON = "in-game admin"
    }
}
