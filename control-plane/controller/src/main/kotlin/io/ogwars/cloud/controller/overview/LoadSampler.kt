package io.ogwars.cloud.controller.overview

import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.overview.LoadHistory
import io.ogwars.cloud.domain.overview.LoadSample
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.LoadHistoryStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Records the network's load once a minute for the dashboard's 24-hour charts (D-0213). */
@Component
class LoadSampler(
    private val groups: GroupStore,
    private val servers: ServerStateStore,
    private val sessions: SessionStore,
    private val history: LoadHistoryStore,
    private val clock: Clock,
) : LeaderLoop {
    override val name = "load-history"
    override val interval: Duration = LoadHistory.INTERVAL

    override fun runOnce() {
        val sample =
            LoadSample.of(
                at = clock.instant(),
                onlinePlayers = sessions.onlinePlayers().size,
                groupIds = groups.findAll().map { it.value.id },
                servers = servers.listAll().map { it.value },
            )
        history.record(sample, LoadHistory.KEPT)
    }
}
