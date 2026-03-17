package io.ogwars.cloud.common.redis

object RedisKeys {
    const val PERMISSION_REENABLE_SYNC_LOCK_KEY = "lock:permission-reenable-sync"
    const val NETWORK_RESTART_SYNC_LOCK_KEY = "lock:network-restart-sync"
    private const val GROUP_RESTART_SYNC_LOCK_KEY_PREFIX = "lock:group-restart-sync:"
    const val GROUP_RESTART_SYNC_LOCK_KEY_PATTERN = "${GROUP_RESTART_SYNC_LOCK_KEY_PREFIX}*"

    fun groupRestartSyncLockKey(groupId: String): String = "$GROUP_RESTART_SYNC_LOCK_KEY_PREFIX$groupId"

    fun groupIdFromGroupRestartSyncLockKey(key: String): String? =
        key
            .takeIf { it.startsWith(GROUP_RESTART_SYNC_LOCK_KEY_PREFIX) }
            ?.removePrefix(GROUP_RESTART_SYNC_LOCK_KEY_PREFIX)
}
