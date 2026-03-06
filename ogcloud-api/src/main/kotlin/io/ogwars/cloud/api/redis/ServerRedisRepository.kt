package io.ogwars.cloud.api.redis

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.model.RedisServerData
import io.ogwars.cloud.api.model.ServerDocument
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class ServerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    fun findById(id: String): ServerDocument? {
        return readServer(SERVER_KEY_PREFIX + id)
    }

    fun findAll(): List<ServerDocument> {
        val ids = redisTemplate.opsForSet().members(ALL_SERVERS_KEY) ?: return emptyList()
        return findByIds(ids)
    }

    fun findByGroup(group: String): List<ServerDocument> {
        val ids = redisTemplate.opsForSet().members(GROUP_SERVERS_PREFIX + group) ?: return emptyList()
        return findByIds(ids)
    }

    private fun findByIds(ids: Collection<String>): List<ServerDocument> {
        return ids.mapNotNull { id -> readServer(SERVER_KEY_PREFIX + id) }
    }

    private fun readServer(key: String): ServerDocument? {
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue(json, RedisServerData::class.java).toDocument()
    }

    companion object {
        private const val SERVER_KEY_PREFIX = "server:"
        private const val ALL_SERVERS_KEY = "servers"
        private const val GROUP_SERVERS_PREFIX = "servers:group:"
    }
}
