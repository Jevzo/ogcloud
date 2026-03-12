package io.ogwars.cloud.paper.permission

import io.ogwars.cloud.common.event.PermissionUpdateEvent
import io.ogwars.cloud.common.model.DisplayConfig
import io.ogwars.cloud.common.model.RedisPlayerSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class CachedPermission(
    val groupId: String,
    val groupName: String,
    val permissions: List<String>,
    val chatPrefix: String,
    val chatSuffix: String,
    val nameColor: String,
    val tabPrefix: String,
    val weight: Int,
)

class PermissionManager {
    private val cache = ConcurrentHashMap<UUID, CachedPermission>()

    fun cachePlayer(
        uuid: UUID,
        session: RedisPlayerSession,
    ) {
        cache[uuid] = session.toCachedPermission()
    }

    fun cachePlayerFromEvent(
        uuid: UUID,
        event: PermissionUpdateEvent,
    ) {
        cache[uuid] = event.toCachedPermission()
    }

    fun cachePlayerDefault(uuid: UUID) {
        cache[uuid] = createDefaultPermission()
    }

    fun removePlayer(uuid: UUID) {
        cache.remove(uuid)
    }

    fun getCachedPlayer(uuid: UUID): CachedPermission? = cache[uuid]

    fun clear() {
        cache.clear()
    }

    fun hasPermission(
        uuid: UUID,
        permission: String,
    ): Boolean {
        val cached = cache[uuid] ?: return false
        return cached.permissions.grants(permission)
    }

    private fun createCachedPermission(
        groupId: String,
        groupName: String,
        permissions: List<String>,
        display: DisplayConfig,
        weight: Int,
    ): CachedPermission =
        CachedPermission(
            groupId = groupId,
            groupName = groupName,
            permissions = permissions,
            chatPrefix = display.chatPrefix,
            chatSuffix = display.chatSuffix,
            nameColor = display.nameColor,
            tabPrefix = display.tabPrefix,
            weight = weight,
        )

    private fun RedisPlayerSession.toCachedPermission(): CachedPermission =
        createCachedPermission(
            groupId = permission.group,
            groupName = permission.group,
            permissions = permissions,
            display = display,
            weight = weight,
        )

    private fun PermissionUpdateEvent.toCachedPermission(): CachedPermission =
        createCachedPermission(
            groupId = groupId,
            groupName = groupName,
            permissions = permissions,
            display = display,
            weight = weight,
        )

    private fun createDefaultPermission(): CachedPermission =
        createCachedPermission(
            groupId = DEFAULT_GROUP_ID,
            groupName = DEFAULT_GROUP_NAME,
            permissions = emptyList(),
            display = DisplayConfig(),
            weight = DEFAULT_WEIGHT,
        )

    private fun List<String>.grants(permission: String): Boolean = WILDCARD_PERMISSION in this || permission in this

    companion object {
        private const val DEFAULT_GROUP_ID = "default"
        private const val DEFAULT_GROUP_NAME = "Default"
        private const val DEFAULT_WEIGHT = 100
        private const val WILDCARD_PERMISSION = "*"
    }
}
