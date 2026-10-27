package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.LockManager
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Deletes authorizations whose tokens all expired (D-0043). The controller's cleanup loop cannot, because its
 * database role has no access to credentials (D-0089); a Valkey lock keeps API replicas from running it at once.
 */
@Component
class AuthorizationCleanup(
    private val jdbc: JdbcClient,
    private val locks: LockManager,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ogcloud.auth.cleanup-interval:15m}")
    fun run() {
        locks.tryAcquire(LOCK, LEASE)?.use {
            val deleted = deleteExpired(clock.instant())
            if (deleted > 0) log.info("Deleted {} expired authorizations", deleted)
        }
    }

    fun deleteExpired(now: Instant): Int {
        var total = 0
        do {
            val deleted =
                jdbc
                    .sql(
                        """
                        delete from oauth2_authorization where id in (
                            select id from oauth2_authorization
                            where greatest(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at) < :now
                            limit :batch)
                        """.trimIndent(),
                    ).param("now", Timestamp.from(now))
                    .param("batch", BATCH)
                    .update()
            total += deleted
        } while (deleted == BATCH)
        return total
    }

    private companion object {
        const val LOCK = "auth-cleanup"
        const val BATCH = 1000
        val LEASE: Duration = Duration.ofMinutes(5)
    }
}
