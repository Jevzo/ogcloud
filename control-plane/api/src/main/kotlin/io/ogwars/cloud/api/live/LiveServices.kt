package io.ogwars.cloud.api.live

import io.ogwars.cloud.api.events.Commands
import io.ogwars.cloud.api.events.TransferDestination
import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
import io.ogwars.cloud.contracts.events.v1.NodeCommand
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Service
import java.util.UUID

/** Servers as the controller keeps them in Valkey; changes go to the controller as commands (D-0101). */
@Service
class LiveServers(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val commands: Commands,
    private val ids: IdGenerator,
) {
    fun list(
        groupId: GroupId?,
        state: ServerState?,
    ): List<Server> =
        (groupId?.let(servers::listByGroup) ?: servers.listAll())
            .map { it.value }
            .filter { state == null || it.state == state }
            .sortedWith(compareBy({ it.groupId.value }, { it.id }))

    fun get(id: UUID): Server = servers.get(id)?.value ?: throw NoSuchElementException("Server $id does not exist")

    /** Returns the new server's id at once; the controller records and starts it (D-0075). */
    fun request(
        actor: Actor,
        groupId: GroupId,
    ): UUID {
        groups.find(groupId) ?: throw NoSuchElementException("Group $groupId does not exist")
        if (!network.load().value.eulaAccepted) throw ConflictException("An owner must accept the Minecraft EULA first")
        val id = ids.next()
        commands.requestServer(actor, id, groupId)
        return id
    }

    fun stop(
        actor: Actor,
        id: UUID,
    ) = commands.stopServer(actor, get(id).id, REASON)

    fun kill(
        actor: Actor,
        id: UUID,
    ) = commands.killServer(actor, get(id).id, REASON)

    fun pushTemplate(
        actor: Actor,
        id: UUID,
    ) {
        if (get(id).groupType == GroupType.PROXY) throw ConflictException("Proxies have no template to push")
        commands.pushTemplate(actor, id)
    }

    /** Runs a console command on every node, one server or every server of a group. */
    fun console(
        actor: Actor,
        target: CommandTarget,
        id: String?,
        command: String,
    ) {
        val line = command.trim().removePrefix("/")
        require(line.isNotBlank()) { "The command is empty" }
        when (target) {
            CommandTarget.ALL -> {
                commands.console(actor, { setAll(true) }, ALL_NODES, line)
            }

            CommandTarget.SERVER -> {
                val server =
                    get(
                        UUID.fromString(id ?: throw IllegalArgumentException("A server command needs the server's id")),
                    )
                commands.console(actor, { setServerId(server.id.toString()) }, server.id.toString(), line)
            }

            CommandTarget.GROUP -> {
                val group = GroupId(id ?: throw IllegalArgumentException("A group command needs the group's id"))
                groups.find(group) ?: throw NoSuchElementException("Group $group does not exist")
                commands.console(actor, { setGroupId(group.value) }, group.value, line)
            }
        }
    }

    private companion object {
        const val REASON = "Requested through the API"
        const val ALL_NODES = "all"
    }
}

/** Players: who is online (Valkey sessions) and everyone ever seen (PostgreSQL). */
@Service
class LivePlayers(
    private val players: PlayerStore,
    private val sessions: SessionStore,
) {
    fun online(
        after: UUID?,
        limit: Int,
    ): Page<PlayerSession> = Cursors.page(sessions.listOnline(after, limit + 1), limit) { it.playerUuid }

    fun known(
        namePrefix: String?,
        after: UUID?,
        limit: Int,
    ): Page<Player> = Cursors.page(players.list(namePrefix, after, limit + 1), limit) { it.uuid }

    fun get(uuid: UUID): Pair<Player, PlayerSession?> {
        val player = players.find(uuid) ?: throw NoSuchElementException("Player $uuid was never seen")
        return player to sessions.get(uuid)?.value
    }

    fun session(uuid: UUID): PlayerSession =
        sessions.get(uuid)?.value ?: throw ConflictException("Player $uuid is not online")
}

/** Moves players; the proxies carry it out. */
@Service
class Transfers(
    private val servers: LiveServers,
    private val players: LivePlayers,
    private val groups: GroupStore,
    private val commands: Commands,
) {
    fun player(
        actor: Actor,
        uuid: UUID,
        request: TransferRequest,
    ) = commands.transferPlayer(actor, players.session(uuid).playerUuid, destination(request))

    fun everyoneOn(
        actor: Actor,
        serverId: UUID,
        request: TransferRequest,
    ) = commands.transferAll(actor, servers.get(serverId).id, destination(request))

    private fun destination(request: TransferRequest): TransferDestination {
        val given = listOfNotNull(request.serverId, request.groupId, request.kickMessage)
        require(given.size == 1) { "Give exactly one of serverId, groupId and kickMessage" }
        return when {
            request.serverId != null -> {
                TransferDestination.Server(servers.get(request.serverId).id)
            }

            request.groupId != null -> {
                val group = GroupId(request.groupId)
                groups.find(group) ?: throw NoSuchElementException("Group $group does not exist")
                TransferDestination.Group(group, request.routingStrategy)
            }

            else -> {
                TransferDestination.Kick(request.kickMessage.orEmpty())
            }
        }
    }
}
