package io.ogwars.cloud.controller.redis

import io.ogwars.cloud.api.model.RedisServerData
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.controller.model.ServerDocument
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@Service
class ServerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val staleMembershipCleanupCount = AtomicLong(0)
    private val staleTimeoutIndexCleanupCount = AtomicLong(0)
    private val staleTimeoutIndexRepairCount = AtomicLong(0)

    fun save(server: ServerDocument) {
        val key = serverKey(server.id)
        val json = objectMapper.writeValueAsString(server.toRedisData())

        redisTemplate.opsForValue().set(key, json)
        redisTemplate.opsForSet().add(ALL_SERVERS_KEY, server.id)
        redisTemplate.opsForSet().add(groupServersKey(server.group), server.id)

        synchronizeTimeoutIndexes(server)
    }

    fun findById(id: String): ServerDocument? = readServer(serverKey(id))

    fun findAll(): List<ServerDocument> {
        val ids = redisTemplate.opsForSet().members(ALL_SERVERS_KEY)?.toList() ?: return emptyList()
        return findByIds(
            ids = ids,
            missingSetKeys = setOf(ALL_SERVERS_KEY),
        )
    }

    fun findByGroup(group: String): List<ServerDocument> {
        val membershipKey = groupServersKey(group)
        val ids = redisTemplate.opsForSet().members(membershipKey)?.toList() ?: return emptyList()
        return findByIds(
            ids = ids,
            missingSetKeys = setOf(ALL_SERVERS_KEY, membershipKey),
            missingTimeoutIndexKeys = setOf(groupDrainingTimeoutIndexKey(group)),
            membershipValidator = { server -> server.group == group },
            invalidSetKeys = setOf(membershipKey),
        )
    }

    fun findStartingServersStartedBefore(cutoff: Instant): List<ServerDocument> =
        findByTimeoutIndex(
            indexKey = STARTING_TIMEOUT_INDEX_KEY,
            maxScore = cutoff.toEpochMilli().toDouble(),
            timestampSelector = ServerDocument::startedAt,
            indexMembershipValidator = { server -> server.state == ServerState.STARTING },
        )

    fun findRunningServersWithHeartbeatBefore(cutoff: Instant): List<ServerDocument> =
        findByTimeoutIndex(
            indexKey = HEARTBEAT_TIMEOUT_INDEX_KEY,
            maxScore = cutoff.toEpochMilli().toDouble(),
            timestampSelector = ServerDocument::lastHeartbeat,
            indexMembershipValidator = { server -> server.state == ServerState.RUNNING },
        )

    fun findDrainingServersDueByGroup(
        drainCutoffByGroup: Map<String, Instant>,
        fallbackCutoff: Instant,
    ): List<ServerDocument> {
        val dueServers = LinkedHashMap<String, ServerDocument>()

        drainCutoffByGroup.forEach { (group, cutoff) ->
            findByTimeoutIndex(
                indexKey = groupDrainingTimeoutIndexKey(group),
                maxScore = cutoff.toEpochMilli().toDouble(),
                timestampSelector = ServerDocument::drainingStartedAt,
                indexMembershipValidator = { server ->
                    server.state == ServerState.DRAINING && server.group == group
                },
                missingSetKeys = setOf(ALL_SERVERS_KEY, groupServersKey(group)),
                missingTimeoutIndexKeys = setOf(groupDrainingTimeoutIndexKey(group)),
            ).forEach { server ->
                dueServers.putIfAbsent(server.id, server)
            }
        }

        val knownGroups = drainCutoffByGroup.keys
        findByTimeoutIndex(
            indexKey = DRAINING_TIMEOUT_INDEX_KEY,
            maxScore = fallbackCutoff.toEpochMilli().toDouble(),
            timestampSelector = ServerDocument::drainingStartedAt,
            indexMembershipValidator = { server -> server.state == ServerState.DRAINING },
            resultFilter = { server -> server.group !in knownGroups },
        ).forEach { server ->
            dueServers.putIfAbsent(server.id, server)
        }

        return dueServers.values.toList()
    }

    fun delete(
        id: String,
        group: String,
    ) {
        redisTemplate.delete(serverKey(id))
        redisTemplate.opsForSet().remove(ALL_SERVERS_KEY, id)
        redisTemplate.opsForSet().remove(groupServersKey(group), id)
        removeFromTimeoutIndexes(id, group)
    }

    fun rebuildTimeoutIndexes(knownGroups: Collection<String>): TimeoutIndexRebuildSummary {
        val servers = findAll()
        val groupsToReset = (knownGroups + servers.map(ServerDocument::group)).toSet()

        clearTimeoutIndexes(groupsToReset)

        var startingCount = 0
        var heartbeatCount = 0
        var drainingCount = 0

        servers.forEach { server ->
            when (addTimeoutIndexes(server)) {
                TimeoutIndexMembership.STARTING -> startingCount++
                TimeoutIndexMembership.HEARTBEAT -> heartbeatCount++
                TimeoutIndexMembership.DRAINING -> drainingCount++
                TimeoutIndexMembership.NONE -> {}
            }
        }

        return TimeoutIndexRebuildSummary(
            liveServerCount = servers.size,
            startingIndexCount = startingCount,
            heartbeatIndexCount = heartbeatCount,
            drainingIndexCount = drainingCount,
            groupIndexCount = groupsToReset.size,
        )
    }

    private fun findByIds(
        ids: Collection<String>,
        missingSetKeys: Set<String> = emptySet(),
        missingTimeoutIndexKeys: Set<String> = emptySet(),
        membershipValidator: ((ServerDocument) -> Boolean)? = null,
        invalidSetKeys: Set<String> = emptySet(),
    ): List<ServerDocument> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        val payloads = redisTemplate.opsForValue().multiGet(ids.map(::serverKey)) ?: return emptyList()
        val servers = mutableListOf<ServerDocument>()
        val missingIds = mutableListOf<String>()
        val invalidIds = mutableListOf<String>()

        ids.zip(payloads).forEach { (id, json) ->
            if (json == null) {
                missingIds += id
                return@forEach
            }

            val server = readServerJson(json)
            if (membershipValidator != null && !membershipValidator(server)) {
                invalidIds += id
            } else {
                servers += server
            }
        }

        pruneMissingServerReferences(missingIds, missingSetKeys, missingTimeoutIndexKeys)
        pruneStaleMembershipMembers(invalidIds, invalidSetKeys)

        return servers
    }

    private fun findByTimeoutIndex(
        indexKey: String,
        maxScore: Double,
        timestampSelector: (ServerDocument) -> Instant?,
        indexMembershipValidator: (ServerDocument) -> Boolean,
        resultFilter: (ServerDocument) -> Boolean = { true },
        missingSetKeys: Set<String> = setOf(ALL_SERVERS_KEY),
        missingTimeoutIndexKeys: Set<String> = emptySet(),
    ): List<ServerDocument> {
        val indexedEntries =
            redisTemplate
                .opsForZSet()
                .rangeByScoreWithScores(indexKey, Double.NEGATIVE_INFINITY, maxScore)
                ?.mapNotNull { tuple ->
                    tuple.value?.let { value ->
                        IndexedServerId(value, tuple.score)
                    }
                } ?: return emptyList()

        if (indexedEntries.isEmpty()) {
            return emptyList()
        }

        val payloads =
            redisTemplate.opsForValue().multiGet(indexedEntries.map { serverKey(it.id) }) ?: return emptyList()
        val servers = mutableListOf<ServerDocument>()
        val missingIds = mutableListOf<String>()
        val staleIndexIds = mutableListOf<String>()
        val repairServers = mutableListOf<ServerDocument>()

        indexedEntries.zip(payloads).forEach { (entry, json) ->
            if (json == null) {
                missingIds += entry.id
                return@forEach
            }

            val server = readServerJson(json)
            if (!isValidIndexMembership(server, entry, timestampSelector, indexMembershipValidator)) {
                staleIndexIds += entry.id
                repairServers += server
                return@forEach
            }

            if (resultFilter(server)) {
                servers += server
            }
        }

        pruneMissingServerReferences(
            ids = missingIds,
            setKeys = missingSetKeys,
            timeoutIndexKeys = missingTimeoutIndexKeys + indexKey,
        )
        pruneStaleTimeoutIndexMembers(indexKey, staleIndexIds)
        repairTimeoutIndexes(repairServers)

        return servers
    }

    private fun readServer(key: String): ServerDocument? {
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return readServerJson(json)
    }

    private fun readServerJson(json: String): ServerDocument =
        objectMapper.readValue(json, RedisServerData::class.java).toDocument()

    private fun isValidIndexMembership(
        server: ServerDocument,
        entry: IndexedServerId,
        timestampSelector: (ServerDocument) -> Instant?,
        indexMembershipValidator: (ServerDocument) -> Boolean,
    ): Boolean {
        val timestamp = timestampSelector(server) ?: return false
        val indexedScore = entry.score ?: return false
        return indexMembershipValidator(server) && indexedScore == timestamp.toEpochMilli().toDouble()
    }

    private fun synchronizeTimeoutIndexes(server: ServerDocument): TimeoutIndexMembership {
        removeFromTimeoutIndexes(server.id, server.group)
        return addTimeoutIndexes(server)
    }

    private fun addTimeoutIndexes(server: ServerDocument): TimeoutIndexMembership =
        when {
            server.state == ServerState.STARTING && server.startedAt != null -> {
                addToTimeoutIndex(STARTING_TIMEOUT_INDEX_KEY, server.id, server.startedAt)
                TimeoutIndexMembership.STARTING
            }

            server.state == ServerState.RUNNING && server.lastHeartbeat != null -> {
                addToTimeoutIndex(HEARTBEAT_TIMEOUT_INDEX_KEY, server.id, server.lastHeartbeat)
                TimeoutIndexMembership.HEARTBEAT
            }

            server.state == ServerState.DRAINING && server.drainingStartedAt != null -> {
                addToTimeoutIndex(DRAINING_TIMEOUT_INDEX_KEY, server.id, server.drainingStartedAt)
                addToTimeoutIndex(groupDrainingTimeoutIndexKey(server.group), server.id, server.drainingStartedAt)
                TimeoutIndexMembership.DRAINING
            }

            else -> TimeoutIndexMembership.NONE
        }

    private fun addToTimeoutIndex(
        indexKey: String,
        serverId: String,
        timestamp: Instant,
    ) {
        redisTemplate.opsForZSet().add(indexKey, serverId, timestamp.toEpochMilli().toDouble())
    }

    private fun removeFromTimeoutIndexes(
        serverId: String,
        group: String? = null,
    ) {
        val keys = baseTimeoutIndexKeys() + listOfNotNull(group?.let(::groupDrainingTimeoutIndexKey))
        keys.forEach { indexKey ->
            redisTemplate.opsForZSet().remove(indexKey, serverId)
        }
    }

    private fun clearTimeoutIndexes(groups: Set<String>) {
        val keys = (baseTimeoutIndexKeys() + groups.map(::groupDrainingTimeoutIndexKey)).toSet()
        if (keys.isEmpty()) {
            return
        }

        redisTemplate.delete(keys)
    }

    private fun pruneMissingServerReferences(
        ids: Collection<String>,
        setKeys: Set<String>,
        timeoutIndexKeys: Set<String>,
    ) {
        if (ids.isEmpty()) {
            return
        }

        setKeys.forEach { key ->
            redisTemplate.opsForSet().remove(key, *ids.toTypedArray())
        }

        val allTimeoutKeys = (baseTimeoutIndexKeys() + timeoutIndexKeys).toSet()
        allTimeoutKeys.forEach { key ->
            redisTemplate.opsForZSet().remove(key, *ids.toTypedArray())
        }

        val total = staleMembershipCleanupCount.addAndGet(ids.size.toLong())
        log.warn(
            "Pruned stale Redis server references: count={}, sampleIds={}, setKeys={}, timeoutIndexKeys={}, cleanupCount={}",
            ids.size,
            ids.take(LOG_SAMPLE_SIZE),
            setKeys,
            allTimeoutKeys,
            total,
        )
    }

    private fun pruneStaleMembershipMembers(
        ids: Collection<String>,
        setKeys: Set<String>,
    ) {
        if (ids.isEmpty() || setKeys.isEmpty()) {
            return
        }

        setKeys.forEach { key ->
            redisTemplate.opsForSet().remove(key, *ids.toTypedArray())
        }

        val total = staleMembershipCleanupCount.addAndGet(ids.size.toLong())
        log.warn(
            "Pruned stale Redis server set members: count={}, sampleIds={}, setKeys={}, cleanupCount={}",
            ids.size,
            ids.take(LOG_SAMPLE_SIZE),
            setKeys,
            total,
        )
    }

    private fun pruneStaleTimeoutIndexMembers(
        indexKey: String,
        ids: Collection<String>,
    ) {
        if (ids.isEmpty()) {
            return
        }

        redisTemplate.opsForZSet().remove(indexKey, *ids.toTypedArray())

        val total = staleTimeoutIndexCleanupCount.addAndGet(ids.size.toLong())
        log.warn(
            "Pruned stale Redis server timeout index members: indexKey={}, count={}, sampleIds={}, cleanupCount={}",
            indexKey,
            ids.size,
            ids.take(LOG_SAMPLE_SIZE),
            total,
        )
    }

    private fun repairTimeoutIndexes(servers: Collection<ServerDocument>) {
        if (servers.isEmpty()) {
            return
        }

        servers.forEach(::synchronizeTimeoutIndexes)

        val total = staleTimeoutIndexRepairCount.addAndGet(servers.size.toLong())
        log.info(
            "Repaired Redis server timeout index memberships: count={}, sampleIds={}, repairCount={}",
            servers.size,
            servers.map(ServerDocument::id).take(LOG_SAMPLE_SIZE),
            total,
        )
    }

    private fun serverKey(id: String): String = SERVER_KEY_PREFIX + id

    private fun groupServersKey(group: String): String = GROUP_SERVERS_PREFIX + group

    private fun groupDrainingTimeoutIndexKey(group: String): String = GROUP_DRAINING_TIMEOUT_INDEX_PREFIX + group

    private fun baseTimeoutIndexKeys(): Set<String> =
        setOf(
            STARTING_TIMEOUT_INDEX_KEY,
            HEARTBEAT_TIMEOUT_INDEX_KEY,
            DRAINING_TIMEOUT_INDEX_KEY,
        )

    data class TimeoutIndexRebuildSummary(
        val liveServerCount: Int,
        val startingIndexCount: Int,
        val heartbeatIndexCount: Int,
        val drainingIndexCount: Int,
        val groupIndexCount: Int,
    )

    private data class IndexedServerId(
        val id: String,
        val score: Double?,
    )

    private enum class TimeoutIndexMembership {
        NONE,
        STARTING,
        HEARTBEAT,
        DRAINING,
    }

    companion object {
        private const val SERVER_KEY_PREFIX = "server:"
        private const val ALL_SERVERS_KEY = "servers"
        private const val GROUP_SERVERS_PREFIX = "servers:group:"
        private const val STARTING_TIMEOUT_INDEX_KEY = "servers:deadline:starting"
        private const val HEARTBEAT_TIMEOUT_INDEX_KEY = "servers:deadline:heartbeat"
        private const val DRAINING_TIMEOUT_INDEX_KEY = "servers:deadline:draining"
        private const val GROUP_DRAINING_TIMEOUT_INDEX_PREFIX = "servers:deadline:draining:group:"
        private const val LOG_SAMPLE_SIZE = 5
    }
}
