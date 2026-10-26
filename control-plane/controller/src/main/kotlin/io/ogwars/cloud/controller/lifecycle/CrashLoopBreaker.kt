package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.domain.crashloop.CrashLoopPolicy
import io.ogwars.cloud.domain.crashloop.CrashLoopStatus
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.CrashFailureStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/** Slows down, then pauses, groups whose servers keep failing before they run (D-0069 latch). */
@Component
class CrashLoopBreaker(
    private val failures: CrashFailureStore,
    private val policy: CrashLoopPolicy,
    private val properties: LifecycleProperties,
    private val events: ServerEvents,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun recordFailure(
        groupId: GroupId,
        serverId: UUID,
    ) {
        failures.record(groupId, serverId, clock.instant(), properties.crashLoop.window)
        status(groupId)
    }

    /** The group's state; reaching the threshold latches the pause and raises an alert once. */
    fun status(groupId: GroupId): CrashLoopStatus {
        val recent = failures.failures(groupId)
        val status = policy.status(recent, failures.isPaused(groupId), clock.instant())
        if (status != CrashLoopStatus.PauseNow) return status
        failures.pause(groupId)
        log.warn("Group {} paused after {} failed starts", groupId, recent.size)
        events.crashLoopPaused(groupId, recent.size)
        return CrashLoopStatus.Paused
    }
}
