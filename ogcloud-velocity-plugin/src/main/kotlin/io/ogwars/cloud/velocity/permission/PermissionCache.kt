package io.ogwars.cloud.velocity.permission

import io.ogwars.cloud.common.event.PermissionUpdateEvent
import io.ogwars.cloud.common.model.DisplayConfig
import io.ogwars.cloud.common.model.PermissionGroupDocument
import io.ogwars.cloud.common.model.RedisPlayerSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class CachedPlayer(
    val groupId: String,
    val groupName: String,
    val permissions: List<String>,
    val chatPrefix: String,
    val chatSuffix: String,
    val nameColor: String,
    val tabPrefix: String,
    val weight: Int,
    val permissionEndMillis: Long,
)

class PermissionCache {
    private val cache = ConcurrentHashMap<UUID, CachedPlayer>()

    @Volatile
    private var defaultGroup: PermissionGroupDocument? = null

    fun setDefaultGroup(group: PermissionGroupDocument) {
        defaultGroup = group
    }

    fun getDefaultGroup(): PermissionGroupDocument? = defaultGroup

    fun cachePlayer(
        uuid: UUID,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
    ) {
        cache[uuid] = group.toCachedPlayer(permissionEndMillis)
    }

    fun cachePlayerFromRedis(
        uuid: UUID,
        session: RedisPlayerSession,
    ) {
        cache[uuid] = session.toCachedPlayer()
    }

    fun cachePlayerFromEvent(
        uuid: UUID,
        event: PermissionUpdateEvent,
    ) {
        cache[uuid] = event.toCachedPlayer()
    }

    fun removePlayer(uuid: UUID) {
        cache.remove(uuid)
    }

    fun getPlayer(uuid: UUID): CachedPlayer? = cache[uuid]

    fun hasPermission(
        uuid: UUID,
        permission: String,
    ): Boolean {
        val cached = cache[uuid] ?: return false
        return cached.permissions.grants(permission)
    }

    fun getAllCachedPlayers(): Map<UUID, CachedPlayer> = cache.toMap()

    fun clear() {
        cache.clear()
    }

    private fun createCachedPlayer(
        groupId: String,
        groupName: String,
        permissions: List<String>,
        display: DisplayConfig,
        weight: Int,
        permissionEndMillis: Long,
    ): CachedPlayer =
        CachedPlayer(
            groupId = groupId,
            groupName = groupName,
            permissions = permissions,
            chatPrefix = display.chatPrefix,
            chatSuffix = display.chatSuffix,
            nameColor = display.nameColor,
            tabPrefix = display.tabPrefix,
            weight = weight,
            permissionEndMillis = permissionEndMillis,
        )

    private fun PermissionGroupDocument.toCachedPlayer(permissionEndMillis: Long): CachedPlayer =
        createCachedPlayer(
            groupId = id,
            groupName = name,
            permissions = permissions,
            display = display,
            weight = weight,
            permissionEndMillis = permissionEndMillis,
        )

    private fun RedisPlayerSession.toCachedPlayer(): CachedPlayer =
        createCachedPlayer(
            groupId = permission.group,
            groupName = permission.group,
            permissions = permissions,
            display = display,
            weight = weight,
            permissionEndMillis = permission.endMillis,
        )

    private fun PermissionUpdateEvent.toCachedPlayer(): CachedPlayer =
        createCachedPlayer(
            groupId = groupId,
            groupName = groupName,
            permissions = permissions,
            display = display,
            weight = weight,
            permissionEndMillis = permissionEndMillis,
        )

    private fun List<String>.grants(permission: String): Boolean = WILDCARD_PERMISSION in this || permission in this

    companion object {
        private const val WILDCARD_PERMISSION = "*"
    }
}
