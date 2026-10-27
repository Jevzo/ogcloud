package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.store.PlayerStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Timed ranks end on the server side: expired players fall back to the default group (v1 did this on the proxy). */
@Component
class RankExpiryLoop(
    private val players: PlayerStore,
    private val permissions: PlayerPermissions,
    private val clock: Clock,
) : LeaderLoop {
    override val name = "rank-expiry"
    override val interval: Duration = Duration.ofSeconds(INTERVAL_SECONDS)

    override fun runOnce() {
        repeat(MAX_BATCHES_PER_RUN) {
            val expired = players.withExpiredRanks(clock.instant(), BATCH_SIZE)
            expired.forEach(permissions::expire)
            if (expired.size < BATCH_SIZE) return
        }
    }

    private companion object {
        const val INTERVAL_SECONDS = 30L
        const val BATCH_SIZE = 200
        const val MAX_BATCHES_PER_RUN = 10
    }
}
