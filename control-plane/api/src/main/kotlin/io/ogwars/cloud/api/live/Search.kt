package io.ogwars.cloud.api.live

import io.ogwars.cloud.api.web.callerPermissions
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.stereotype.Service

/** The dashboard's quick search over groups, servers and players; each section needs its area's read permission. */
@Service
class Search(
    private val groups: GroupStore,
    private val servers: ServerStateStore,
    private val players: PlayerStore,
    private val sessions: SessionStore,
) {
    fun find(query: String): SearchResult {
        val term = query.trim().lowercase()
        require(term.length >= MIN_TERM) { "Search for at least $MIN_TERM characters" }
        val granted = callerPermissions()
        return SearchResult(
            groups = section(granted, ApiPermission.GROUPS_READ) { groups(term) },
            servers = section(granted, ApiPermission.SERVERS_READ) { servers(term) },
            players = section(granted, ApiPermission.PLAYERS_READ) { players(term) },
        )
    }

    private fun groups(term: String) =
        groups
            .findAll()
            .map { it.value.id.value }
            .filter { term in it }
            .take(MAX_HITS)

    // Pod names use the 32-character hex form of the id (D-0064).
    private fun servers(term: String) =
        servers
            .listAll()
            .map { it.value }
            .filter {
                it.id
                    .toString()
                    .replace("-", "")
                    .startsWith(term.replace("-", "")) ||
                    "${it.groupId}-${it.id}".contains(term)
            }.take(MAX_HITS)
            .map { it.toView() }

    private fun players(term: String) =
        players.list(term, after = null, limit = MAX_HITS).map {
            PlayerSummary(
                it.uuid,
                it.name,
                sessions.get(it.uuid) != null,
            )
        }

    private fun <T> section(
        granted: Set<String>,
        permission: ApiPermission,
        hits: () -> List<T>,
    ): List<T>? = if (permission.scope in granted) hits() else null

    private companion object {
        const val MIN_TERM = 2
        const val MAX_HITS = 10
    }
}
