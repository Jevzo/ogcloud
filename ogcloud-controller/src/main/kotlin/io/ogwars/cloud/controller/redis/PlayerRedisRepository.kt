package io.ogwars.cloud.controller.redis

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.model.RedisPlayerSession
import io.ogwars.cloud.api.model.SessionPermission
import io.ogwars.cloud.controller.model.PermissionGroupDocument
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class PlayerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun saveSession(
        uuid: String,
        name: String,
        proxyId: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
    ) {
        persistSession(
            uuid,
            group.toSession(
                name = name,
                proxyId = proxyId,
                permissionEndMillis = permissionEndMillis,
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
    ) {
        updateSession(uuid) { session -> session.copy(serverId = serverId) }
    }

    fun updatePermissions(
        uuid: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
    ) {
        updateSession(uuid) { session ->
            session.copy(
                permission = group.toSessionPermission(permissionEndMillis),
                display = group.display,
                weight = group.weight,
                permissions = group.permissions,
            )
        }
    }

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
        transform: (RedisPlayerSession) -> RedisPlayerSession,
    ) {
        val session = findPlayerData(uuid) ?: return
        persistSession(uuid, transform(session))
    }

    private fun persistSession(
        uuid: String,
        session: RedisPlayerSession,
    ) {
        redisTemplate.opsForValue().set(sessionKey(uuid), objectMapper.writeValueAsString(session))
    }

    private fun sessionKey(uuid: String): String = PLAYER_KEY_PREFIX + uuid

    private fun PermissionGroupDocument.toSession(
        name: String,
        proxyId: String,
        permissionEndMillis: Long,
    ): RedisPlayerSession =
        RedisPlayerSession(
            name = name,
            proxyId = proxyId,
            connectedAt = System.currentTimeMillis(),
            permission = toSessionPermission(permissionEndMillis),
            display = display,
            weight = weight,
            permissions = permissions,
        )

    private fun PermissionGroupDocument.toSessionPermission(permissionEndMillis: Long): SessionPermission =
        SessionPermission(group = id, endMillis = permissionEndMillis)

    companion object {
        private const val PLAYER_KEY_PREFIX = "player:"
        private const val ONLINE_PLAYERS_KEY = "online_players"
    }
}
