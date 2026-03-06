package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.PaginationSupport
import io.ogwars.cloud.api.dto.SearchResponse
import io.ogwars.cloud.api.dto.toSearchResult
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.api.repository.PlayerRepository
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val groupRepository: GroupRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val playerRepository: PlayerRepository,
    private val playerRedisRepository: PlayerRedisRepository
) {

    fun search(query: String, limit: Int?): SearchResponse {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isEmpty()) {
            throw IllegalArgumentException("Search query must not be blank")
        }

        val resolvedLimit = limit?.coerceIn(1, MAX_RESULTS_PER_SECTION) ?: DEFAULT_RESULTS_PER_SECTION

        val groups = groupRepository.findAll()
            .filter { group -> PaginationSupport.matchesQuery(normalizedQuery, group.id) }
            .sortedBy { it.id }
            .take(resolvedLimit)
            .map { it.toSearchResult() }

        val servers = serverRedisRepository.findAll().filter { server ->
            PaginationSupport.matchesQuery(
                normalizedQuery,
                server.group,
                server.id,
                server.displayName,
                server.podName,
                server.podIp
            )
        }.sortedWith(
            compareBy(
                { it.group },
                { it.displayName.lowercase() },
                { it.id }
            )
        ).take(resolvedLimit).map { it.toSearchResult() }

        val players = playerRepository.findAll().asSequence().map { player ->
            val session = playerRedisRepository.findPlayerData(player.id)
            Triple(
                player,
                session,
                player.toSearchResult(session)
            )
        }.filter { (player, session, _) ->
            PaginationSupport.matchesQuery(
                normalizedQuery,
                player.id,
                player.name,
                session?.name
            )
        }.sortedWith(
            compareBy(
                { it.third.name.lowercase() },
                { it.third.uuid }
            )
        ).take(resolvedLimit).map { it.third }.toList()

        return SearchResponse(
            query = normalizedQuery,
            limit = resolvedLimit,
            groups = groups,
            servers = servers,
            players = players
        )
    }

    companion object {
        private const val DEFAULT_RESULTS_PER_SECTION = 10
        private const val MAX_RESULTS_PER_SECTION = 50
    }
}
