package io.ogwars.cloud.velocity.redis

import io.ogwars.cloud.common.model.RedisPlayerSession
import io.ogwars.cloud.common.model.RedisServerData
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.Logger
import java.net.InetSocketAddress

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

    fun loadRunningServers(serverRegistry: ServerRegistry) {
        var count = 0

        for (id in getServerIds()) {
            val data = getServerData(id) ?: continue
            if (!data.isTransferEligible()) {
                continue
            }

            val podIp = data.podIp ?: continue
            serverRegistry.registerServer(id, data.group, InetSocketAddress(podIp, data.port), data.displayName)
            count += 1
        }

        logger.info("Loaded {} existing servers from Redis", count)
    }

    fun getServerData(id: String): RedisServerData? = readValue(serverKey(id), RedisServerData::class.java)

    fun getServerIds(): Set<String> = readMembers(SERVERS_KEY)

    fun getServerIdsByGroup(group: String): Set<String> = readMembers(groupServersKey(group))

    fun getPlayerData(uuid: String): RedisPlayerSession? = readValue(playerKey(uuid), RedisPlayerSession::class.java)

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
            logger.warn("Failed to deserialize Redis value for {}", key, exception)
            null
        }

    private fun RedisServerData.isTransferEligible(): Boolean = state == RUNNING_STATE && type != PROXY_TYPE

    private fun commands() = connection.sync()

    private fun playerKey(uuid: String): String = "player:$uuid"

    private fun serverKey(id: String): String = "server:$id"

    private fun groupServersKey(group: String): String = "servers:group:$group"

    companion object {
        private const val SERVERS_KEY = "servers"
        private const val RUNNING_STATE = "RUNNING"
        private const val PROXY_TYPE = "PROXY"
    }
}
