package io.ogwars.cloud.api.redis

import io.ogwars.cloud.common.model.RedisPlayerSession
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class PlayerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun findOnlinePlayerUuids(): Set<String> = redisTemplate.opsForSet().members(ONLINE_PLAYERS_KEY) ?: emptySet()

    fun findPlayerData(uuid: String): RedisPlayerSession? {
        val json = redisTemplate.opsForValue().get(PLAYER_KEY_PREFIX + uuid) ?: return null
        return objectMapper.readValue(json, RedisPlayerSession::class.java)
    }

    fun isOnline(uuid: String): Boolean = redisTemplate.opsForSet().isMember(ONLINE_PLAYERS_KEY, uuid) == true

    companion object {
        private const val PLAYER_KEY_PREFIX = "player:"
        private const val ONLINE_PLAYERS_KEY = "online_players"
    }
}
