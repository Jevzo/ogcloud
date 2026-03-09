package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.model.PermissionGroupDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class PlayerConnectRuntimeState {
    private val log = LoggerFactory.getLogger(javaClass)
    private val permissionSystemEnabled = AtomicBoolean(false)
    private val defaultPermissionGroupId = AtomicReference<String?>(null)
    private val permissionGroups = ConcurrentHashMap<String, PermissionGroupDocument>()
    private val recentConnects = ConcurrentHashMap<String, Long>()
    private val lastCleanupAt = AtomicLong(0L)

    fun initialize(
        initialPermissionSystemEnabled: Boolean,
        groups: Collection<PermissionGroupDocument>,
    ) {
        permissionSystemEnabled.set(initialPermissionSystemEnabled)
        permissionGroups.clear()

        groups.forEach { group ->
            permissionGroups[group.id] = group
        }

        val defaultGroupId = groups.firstOrNull { it.default }?.id
        defaultPermissionGroupId.set(defaultGroupId)

        log.info(
            "Initialized connect runtime state: permissionSystemEnabled={}, permissionGroups={}, defaultGroupId={}",
            initialPermissionSystemEnabled,
            permissionGroups.size,
            defaultGroupId,
        )
    }

    fun isPermissionSystemEnabled(): Boolean = permissionSystemEnabled.get()

    fun updatePermissionSystemEnabled(enabled: Boolean) {
        permissionSystemEnabled.set(enabled)
    }

    fun markDefaultPermissionGroup(groupId: String) {
        defaultPermissionGroupId.set(groupId)
    }

    fun upsertPermissionGroup(group: PermissionGroupDocument) {
        permissionGroups[group.id] = group

        if (group.default) {
            defaultPermissionGroupId.set(group.id)
        } else if (defaultPermissionGroupId.get() == group.id) {
            defaultPermissionGroupId.set(permissionGroups.values.firstOrNull { it.default }?.id)
        }
    }

    fun removePermissionGroup(groupId: String) {
        permissionGroups.remove(groupId)

        if (defaultPermissionGroupId.get() == groupId) {
            defaultPermissionGroupId.set(permissionGroups.values.firstOrNull { it.default }?.id)
        }
    }

    fun findPermissionGroup(groupId: String): PermissionGroupDocument? = permissionGroups[groupId]

    fun requireDefaultPermissionGroup(): PermissionGroupDocument {
        val defaultGroupId = defaultPermissionGroupId.get()
        if (defaultGroupId != null) {
            permissionGroups[defaultGroupId]?.let { return it }
        }

        permissionGroups.values.firstOrNull { it.default }?.let { return it }

        throw IllegalStateException(NO_DEFAULT_PERMISSION_GROUP_MESSAGE)
    }

    fun tryStartConnect(
        uuid: String,
        proxyId: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        cleanupExpiredConnectEntries(now)

        val key = connectKey(uuid, proxyId)
        var accepted = false

        recentConnects.compute(key) { _, previous ->
            if (previous == null || now - previous >= CONNECT_DEDUPE_WINDOW_MS) {
                accepted = true
                now
            } else {
                accepted = false
                previous
            }
        }

        return accepted
    }

    fun resetConnectDedupe(uuid: String) {
        val prefix = "$uuid|"
        val iterator = recentConnects.keys.iterator()

        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.startsWith(prefix)) {
                iterator.remove()
            }
        }
    }

    private fun cleanupExpiredConnectEntries(now: Long) {
        val previousCleanup = lastCleanupAt.get()
        if (now - previousCleanup < CONNECT_DEDUPE_WINDOW_MS) {
            return
        }

        if (!lastCleanupAt.compareAndSet(previousCleanup, now)) {
            return
        }

        val expiryThreshold = now - CONNECT_DEDUPE_WINDOW_MS
        val iterator = recentConnects.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < expiryThreshold) {
                iterator.remove()
            }
        }
    }

    private fun connectKey(
        uuid: String,
        proxyId: String,
    ): String = "$uuid|$proxyId"

    companion object {
        private const val CONNECT_DEDUPE_WINDOW_MS = 30_000L
        private const val NO_DEFAULT_PERMISSION_GROUP_MESSAGE = "No default permission group configured"
    }
}
