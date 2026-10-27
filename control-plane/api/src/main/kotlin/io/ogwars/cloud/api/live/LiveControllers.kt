package io.ogwars.cloud.api.live

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/servers")
class ServerController(
    private val servers: LiveServers,
    private val transfers: Transfers,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.SERVERS_READ)
    @GetMapping
    fun list(
        @RequestParam groupId: String?,
        @RequestParam state: ServerState?,
    ): List<ServerView> = servers.list(groupId?.let(::GroupId), state).map { it.toView() }

    @Requires(ApiPermission.SERVERS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ServerView = servers.get(id).toView()

    @Requires(ApiPermission.SERVERS_COMMAND)
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(
        @RequestBody request: RequestServerRequest,
    ): RequestedServer = RequestedServer(servers.request(actors.current(), GroupId(request.groupId)))

    /** Graceful: a running server drains first. */
    @Requires(ApiPermission.SERVERS_COMMAND)
    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun stop(
        @PathVariable id: UUID,
    ) = servers.stop(actors.current(), id)

    @Requires(ApiPermission.SERVERS_COMMAND)
    @PostMapping("/{id}/kill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun kill(
        @PathVariable id: UUID,
    ) = servers.kill(actors.current(), id)

    /** Saves the server's files as a new template version (D-0042). */
    @Requires(ApiPermission.TEMPLATES_WRITE)
    @PostMapping("/{id}/template-push")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun pushTemplate(
        @PathVariable id: UUID,
    ) = servers.pushTemplate(actors.current(), id)

    @Requires(ApiPermission.PLAYERS_WRITE)
    @PostMapping("/{id}/transfer-all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun transferAll(
        @PathVariable id: UUID,
        @RequestBody request: TransferRequest,
    ) = transfers.everyoneOn(actors.current(), id, request)
}

@RestController
@RequestMapping("/api/v1/commands")
class CommandController(
    private val servers: LiveServers,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.SERVERS_COMMAND)
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun run(
        @RequestBody request: ConsoleCommandRequest,
    ) = servers.console(actors.current(), request.target, request.id, request.command)
}

@RestController
@RequestMapping("/api/v1/players")
class PlayerController(
    private val players: LivePlayers,
    private val transfers: Transfers,
    private val actors: CurrentActor,
) {
    /** Online players, ordered by uuid so the cursor stays valid while people come and go. */
    @Requires(ApiPermission.PLAYERS_READ)
    @GetMapping
    fun online(
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<OnlinePlayerView> {
        val page = players.online(cursor?.let(Cursors::decode), Cursors.limit(limit))
        return Page(page.items.map { it.toView() }, page.nextCursor)
    }

    /** Everyone ever seen; [name] narrows by prefix. */
    @Requires(ApiPermission.PLAYERS_READ)
    @GetMapping("/known")
    fun known(
        @RequestParam name: String?,
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<PlayerView> {
        val page = players.known(name, cursor?.let(Cursors::decode), Cursors.limit(limit))
        return Page(page.items.map { it.toView(session = null) }, page.nextCursor)
    }

    @Requires(ApiPermission.PLAYERS_READ)
    @GetMapping("/{uuid}")
    fun get(
        @PathVariable uuid: UUID,
    ): PlayerView = players.get(uuid).let { (player, session) -> player.toView(session) }

    @Requires(ApiPermission.PLAYERS_WRITE)
    @PostMapping("/{uuid}/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun transfer(
        @PathVariable uuid: UUID,
        @RequestBody request: TransferRequest,
    ) = transfers.player(actors.current(), uuid, request)
}

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val search: Search,
) {
    @Requires(ApiPermission.OVERVIEW_READ)
    @GetMapping
    fun find(
        @RequestParam q: String,
    ): SearchResult = search.find(q)
}

/** A group's crash-loop breaker; resuming clears the latch and the failures (D-0069). */
@RestController
@RequestMapping("/api/v1/groups/{id}/crash-loop")
class CrashLoopController(
    private val failures: CrashFailureStore,
    private val groups: GroupStore,
) {
    @Requires(ApiPermission.GROUPS_READ)
    @GetMapping
    fun get(
        @PathVariable id: String,
    ): CrashLoopView = view(existing(id))

    @Requires(ApiPermission.GROUPS_WRITE)
    @PostMapping("/resume")
    fun resume(
        @PathVariable id: String,
    ): CrashLoopView {
        val group = existing(id)
        failures.clear(group)
        return view(group)
    }

    private fun existing(id: String): GroupId =
        GroupId(id).also {
            groups.find(it)
                ?: throw NoSuchElementException("Group $it does not exist")
        }

    private fun view(group: GroupId) = CrashLoopView(failures.failures(group).sorted(), failures.isPaused(group))
}
