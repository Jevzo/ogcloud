package io.ogwars.cloud.api.service

import io.ogwars.cloud.common.redis.RedisKeys
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class RestartSyncLockService(
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun acquireGroupRestartLock(groupId: String): String? = acquireLock(RedisKeys.groupRestartSyncLockKey(groupId))

    fun releaseGroupRestartLock(
        groupId: String,
        lockToken: String,
    ) {
        releaseLock(RedisKeys.groupRestartSyncLockKey(groupId), lockToken)
    }

    fun isGroupRestartLockActive(groupId: String): Boolean =
        redisTemplate.hasKey(RedisKeys.groupRestartSyncLockKey(groupId))

    fun hasAnyGroupRestartLockActive(): Boolean =
        redisTemplate.keys(RedisKeys.GROUP_RESTART_SYNC_LOCK_KEY_PATTERN).isNotEmpty()

    fun acquireNetworkRestartLock(): String? = acquireLock(RedisKeys.NETWORK_RESTART_SYNC_LOCK_KEY)

    fun releaseNetworkRestartLock(lockToken: String) {
        releaseLock(RedisKeys.NETWORK_RESTART_SYNC_LOCK_KEY, lockToken)
    }

    fun isNetworkRestartLockActive(): Boolean = redisTemplate.hasKey(RedisKeys.NETWORK_RESTART_SYNC_LOCK_KEY)

    private fun acquireLock(key: String): String? {
        val lockToken = UUID.randomUUID().toString()
        return lockToken.takeIf {
            redisTemplate.opsForValue().setIfAbsent(key, lockToken, LOCK_TTL) == true
        }
    }

    private fun releaseLock(
        key: String,
        lockToken: String,
    ) {
        val currentLockToken = redisTemplate.opsForValue().get(key) ?: return
        if (currentLockToken != lockToken) {
            log.warn(
                "Restart sync lock ownership changed before release; key={}, expectedToken={}, actualToken={}",
                key,
                lockToken,
                currentLockToken,
            )
            return
        }

        redisTemplate.delete(key)
    }

    companion object {
        private val LOCK_TTL: Duration = Duration.ofMinutes(30)
    }
}
