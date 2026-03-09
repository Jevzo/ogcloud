package io.ogwars.cloud.controller.redis

import io.ogwars.cloud.api.model.DisplayConfig
import io.ogwars.cloud.api.model.RedisPlayerSession
import io.ogwars.cloud.api.model.SessionPermission
import io.ogwars.cloud.controller.model.PermissionGroupDocument
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

@Service
class PlayerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val conflictRetryCount = AtomicLong(0)
    private val versionRejectedCount = AtomicLong(0)
    private val missingSessionNoopCount = AtomicLong(0)
    private val retryExhaustedCount = AtomicLong(0)

    fun saveSession(
        uuid: String,
        name: String,
        proxyId: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
        permissionVersion: Long,
    ) {
        persistSession(
            uuid,
            group.toSession(
                name = name,
                proxyId = proxyId,
                permissionEndMillis = permissionEndMillis,
                permissionVersion = permissionVersion,
            ),
        )
        redisTemplate.opsForSet().add(ONLINE_PLAYERS_KEY, uuid)
    }

    fun saveSession(
        uuid: String,
        name: String,
        proxyId: String,
    ) {
        persistSession(
            uuid,
            RedisPlayerSession(
                name = name,
                proxyId = proxyId,
                connectedAt = System.currentTimeMillis(),
            ),
        )
        redisTemplate.opsForSet().add(ONLINE_PLAYERS_KEY, uuid)
    }

    fun updateServerId(
        uuid: String,
        serverId: String,
    ): SessionUpdateOutcome =
        updateSession(
            uuid = uuid,
            transform = { session ->
                SessionMutationDecision.Apply(session.copy(serverId = serverId))
            },
        )

    fun updatePermissions(
        uuid: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
        permissionVersion: Long,
    ): SessionUpdateOutcome =
        updatePermissions(
            uuid = uuid,
            groupId = group.id,
            display = group.display,
            weight = group.weight,
            permissions = group.permissions,
            permissionEndMillis = permissionEndMillis,
            permissionVersion = permissionVersion,
        )

    fun updatePermissions(
        uuid: String,
        groupId: String,
        display: DisplayConfig,
        weight: Int,
        permissions: List<String>,
        permissionEndMillis: Long,
        permissionVersion: Long,
    ): SessionUpdateOutcome =
        updateSession(
            uuid = uuid,
            transform = { session ->
                if (permissionVersion <= session.permission.version) {
                    SessionMutationDecision.VersionRejected
                } else {
                    SessionMutationDecision.Apply(
                        session.copy(
                            permission =
                                SessionPermission(
                                    group = groupId,
                                    endMillis = permissionEndMillis,
                                    version = permissionVersion,
                                ),
                            display = display,
                            weight = weight,
                            permissions = permissions,
                        ),
                    )
                }
            },
        )

    fun findPlayerData(uuid: String): RedisPlayerSession? {
        val json = redisTemplate.opsForValue().get(sessionKey(uuid)) ?: return null
        return objectMapper.readValue(json, RedisPlayerSession::class.java)
    }

    fun deleteSession(uuid: String) {
        redisTemplate.delete(sessionKey(uuid))
        redisTemplate.opsForSet().remove(ONLINE_PLAYERS_KEY, uuid)
    }

    fun isOnline(uuid: String): Boolean = redisTemplate.opsForSet().isMember(ONLINE_PLAYERS_KEY, uuid) == true

    fun findOnlinePlayerUuids(): Set<String> = redisTemplate.opsForSet().members(ONLINE_PLAYERS_KEY) ?: emptySet()

    private fun updateSession(
        uuid: String,
        transform: (RedisPlayerSession) -> SessionMutationDecision,
    ): SessionUpdateOutcome {
        repeat(MAX_TRANSACTION_RETRIES) { attemptIndex ->
            val key = sessionKey(uuid)
            val attemptResult =
                redisTemplate.execute { connection ->
                    val keyBytes = serialize(key)
                    connection.watch(keyBytes)

                    val currentJsonBytes = connection.stringCommands().get(keyBytes)
                    if (currentJsonBytes == null) {
                        connection.unwatch()
                        return@execute SessionUpdateAttemptResult.Missing
                    }

                    val currentSession =
                        objectMapper.readValue(
                            deserialize(currentJsonBytes),
                            RedisPlayerSession::class.java,
                        )
                    when (val mutationDecision = transform(currentSession)) {
                        SessionMutationDecision.VersionRejected -> {
                            connection.unwatch()
                            SessionUpdateAttemptResult.VersionRejected
                        }

                        is SessionMutationDecision.Apply -> {
                            connection.multi()
                            connection.stringCommands().set(
                                keyBytes,
                                serialize(objectMapper.writeValueAsString(mutationDecision.session)),
                            )
                            val execResult = connection.exec()
                            if (execResult.isNullOrEmpty()) {
                                SessionUpdateAttemptResult.Conflict
                            } else {
                                SessionUpdateAttemptResult.Updated
                            }
                        }
                    }
                } ?: SessionUpdateAttemptResult.Conflict

            when (attemptResult) {
                SessionUpdateAttemptResult.Updated -> return SessionUpdateOutcome.UPDATED
                SessionUpdateAttemptResult.Missing -> {
                    val totalMissing = missingSessionNoopCount.incrementAndGet()
                    log.debug(
                        "Redis session update no-op: missing session key, uuid={}, missingSessionCount={}",
                        uuid,
                        totalMissing,
                    )
                    return SessionUpdateOutcome.MISSING
                }

                SessionUpdateAttemptResult.VersionRejected -> {
                    val totalRejected = versionRejectedCount.incrementAndGet()
                    log.debug(
                        "Redis session update rejected by permission version gate: uuid={}, versionRejectedCount={}",
                        uuid,
                        totalRejected,
                    )
                    return SessionUpdateOutcome.VERSION_REJECTED
                }

                SessionUpdateAttemptResult.Conflict -> {
                    val totalConflicts = conflictRetryCount.incrementAndGet()
                    log.debug(
                        "Redis session optimistic transaction conflict: uuid={}, attempt={}, maxAttempts={}, conflictRetryCount={}",
                        uuid,
                        attemptIndex + 1,
                        MAX_TRANSACTION_RETRIES,
                        totalConflicts,
                    )
                }
            }
        }

        val totalRetryExhausted = retryExhaustedCount.incrementAndGet()
        log.warn(
            "Redis session update retry exhaustion: uuid={}, maxAttempts={}, retryExhaustedCount={}",
            uuid,
            MAX_TRANSACTION_RETRIES,
            totalRetryExhausted,
        )
        return SessionUpdateOutcome.RETRY_EXHAUSTED
    }

    private fun persistSession(
        uuid: String,
        session: RedisPlayerSession,
    ) {
        redisTemplate.opsForValue().set(sessionKey(uuid), objectMapper.writeValueAsString(session))
    }

    private fun serialize(value: String): ByteArray =
        checkNotNull(redisTemplate.stringSerializer.serialize(value)) {
            "Failed to serialize Redis string value."
        }

    private fun deserialize(value: ByteArray): String =
        checkNotNull(redisTemplate.stringSerializer.deserialize(value)) {
            "Failed to deserialize Redis string value."
        }

    private fun sessionKey(uuid: String): String = PLAYER_KEY_PREFIX + uuid

    private fun PermissionGroupDocument.toSession(
        name: String,
        proxyId: String,
        permissionEndMillis: Long,
        permissionVersion: Long,
    ): RedisPlayerSession =
        RedisPlayerSession(
            name = name,
            proxyId = proxyId,
            connectedAt = System.currentTimeMillis(),
            permission = toSessionPermission(permissionEndMillis, permissionVersion),
            display = display,
            weight = weight,
            permissions = permissions,
        )

    private fun PermissionGroupDocument.toSessionPermission(
        permissionEndMillis: Long,
        permissionVersion: Long,
    ): SessionPermission =
        SessionPermission(
            group = id,
            endMillis = permissionEndMillis,
            version = permissionVersion,
        )

    companion object {
        private const val PLAYER_KEY_PREFIX = "player:"
        private const val ONLINE_PLAYERS_KEY = "online_players"
        private const val MAX_TRANSACTION_RETRIES = 8
    }

    private sealed interface SessionMutationDecision {
        data class Apply(
            val session: RedisPlayerSession,
        ) : SessionMutationDecision

        object VersionRejected : SessionMutationDecision
    }

    private enum class SessionUpdateAttemptResult {
        Updated,
        Missing,
        VersionRejected,
        Conflict,
    }

    enum class SessionUpdateOutcome {
        UPDATED,
        MISSING,
        VERSION_REJECTED,
        RETRY_EXHAUSTED,
    }
}
