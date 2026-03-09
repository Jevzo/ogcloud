package io.ogwars.cloud.paper.redis

import io.ogwars.cloud.api.model.RedisPlayerSession
import io.ogwars.cloud.api.model.RedisServerData
import com.google.gson.Gson
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import java.util.logging.Logger

class RedisManager(
    redisHost: String,
    redisPort: Int,
    private val logger: Logger,
) {
    private val client: RedisClient =
        RedisClient.create(
            RedisURI
                .builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build(),
        )

    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val gson = Gson()

    fun getPlayerData(uuid: String): RedisPlayerSession? = readValue(playerKey(uuid), RedisPlayerSession::class.java)

    fun getServerData(id: String): RedisServerData? = readValue(serverKey(id), RedisServerData::class.java)

    fun getServerIds(): Set<String> = readMembers(SERVERS_KEY)

    fun getServerIdsByGroup(group: String): Set<String> = readMembers(groupServersKey(group))

    fun close() {
        connection.close()
        client.shutdown()
    }

    private fun <T> readValue(
        key: String,
        type: Class<T>,
    ): T? {
        val json = commands().get(key) ?: return null
        return deserialize(key, json, type)
    }

    private fun readMembers(key: String): Set<String> = commands().smembers(key) ?: emptySet()

    private fun <T> deserialize(
        key: String,
        json: String,
        type: Class<T>,
    ): T? =
        try {
            gson.fromJson(json, type)
        } catch (exception: Exception) {
            logger.warning("Failed to deserialize Redis value for $key: ${exception.message}")
            null
        }

    private fun commands() = connection.sync()

    private fun playerKey(uuid: String): String = "player:$uuid"

    private fun serverKey(id: String): String = "server:$id"

    private fun groupServersKey(group: String): String = "servers:group:$group"

    companion object {
        private const val SERVERS_KEY = "servers"
    }
}
