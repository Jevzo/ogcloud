package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.controller.leader.LeaderLoop
import org.springframework.stereotype.Component
import java.time.Duration

/** Repairs read models that missed an announcement or a Valkey failover lost (D-0048, D-0100). */
@Component
class ReadModelResync(
    private val sync: ReadModelSync,
) : LeaderLoop {
    override val name = "read-model-resync"
    override val interval: Duration = Duration.ofMinutes(INTERVAL_MINUTES)

    override fun runOnce() = sync.resyncAll()

    private companion object {
        const val INTERVAL_MINUTES = 5L
    }
}
