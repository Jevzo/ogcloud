package io.ogwars.cloud.controller.redis

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.model.RedisServerData
import io.ogwars.cloud.controller.model.ServerDocument
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class ServerRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    fun save(server: ServerDocument) {
        val key = serverKey(server.id)
        val json = objectMapper.writeValueAsString(server.toRedisData())

        redisTemplate.opsForValue().set(key, json)
        redisTemplate.opsForSet().add(ALL_SERVERS_KEY, server.id)
        redisTemplate.opsForSet().add(groupServersKey(server.group), server.id)
    }

    fun findById(id: String): ServerDocument? {
        return readServer(serverKey(id))
    }

    fun findAll(): List<ServerDocument> {
        val ids = redisTemplate.opsForSet().members(ALL_SERVERS_KEY) ?: return emptyList()
        return findByIds(ids)
    }

    fun findByGroup(group: String): List<ServerDocument> {
        val ids = redisTemplate.opsForSet().members(groupServersKey(group)) ?: return emptyList()
        return findByIds(ids)
    }

    fun delete(id: String, group: String) {
        redisTemplate.delete(serverKey(id))
        redisTemplate.opsForSet().remove(ALL_SERVERS_KEY, id)
        redisTemplate.opsForSet().remove(groupServersKey(group), id)
    }

    private fun findByIds(ids: Collection<String>): List<ServerDocument> {
        return ids.mapNotNull { id -> readServer(serverKey(id)) }
    }

    private fun readServer(key: String): ServerDocument? {
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue(json, RedisServerData::class.java).toDocument()
    }

    private fun serverKey(id: String): String = SERVER_KEY_PREFIX + id

    private fun groupServersKey(group: String): String = GROUP_SERVERS_PREFIX + group

    companion object {
        private const val SERVER_KEY_PREFIX = "server:"
        private const val ALL_SERVERS_KEY = "servers"
        private const val GROUP_SERVERS_PREFIX = "servers:group:"
    }
}
