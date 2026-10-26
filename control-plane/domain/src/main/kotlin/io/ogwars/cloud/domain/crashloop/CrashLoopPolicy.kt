package io.ogwars.cloud.domain.crashloop

import java.time.Duration
import java.time.Instant

data class CrashLoopSettings(
    /** Only failures this recent count. */
    val window: Duration = Duration.ofMinutes(10),
    /** This many failures within the window pause the group until an admin resumes it. */
    val pauseAfter: Int = 5,
    val firstBackoff: Duration = Duration.ofSeconds(15),
    val maxBackoff: Duration = Duration.ofMinutes(5),
) {
    init {
        require(pauseAfter > 0) { "pauseAfter must be positive" }
        require(firstBackoff.isPositive && maxBackoff >= firstBackoff) { "backoffs must be positive and ordered" }
    }
}

sealed interface CrashLoopStatus {
    data object Healthy : CrashLoopStatus

    /** New servers wait until [until]; each further failure doubles the wait. */
    data class BackingOff(
        val until: Instant,
    ) : CrashLoopStatus

    /** Latched: stays until an admin resumes the group, even after old failures age out. */
    data object Paused : CrashLoopStatus

    /** The threshold was just reached; the caller latches the pause. */
    data object PauseNow : CrashLoopStatus
}

/** A group whose servers keep failing before they run is slowed down, then paused (no pod churn). */
class CrashLoopPolicy(
    private val settings: CrashLoopSettings = CrashLoopSettings(),
) {
    fun status(
        failures: List<Instant>,
        pausedByLatch: Boolean,
        now: Instant,
    ): CrashLoopStatus {
        if (pausedByLatch) return CrashLoopStatus.Paused
        val recent = failures.filter { Duration.between(it, now) <= settings.window }.sorted()
        if (recent.isEmpty()) return CrashLoopStatus.Healthy
        if (recent.size >= settings.pauseAfter) return CrashLoopStatus.PauseNow
        val until = recent.last().plus(backoff(recent.size))
        return if (now.isBefore(until)) CrashLoopStatus.BackingOff(until) else CrashLoopStatus.Healthy
    }

    private fun backoff(failureCount: Int): Duration {
        val doubled = settings.firstBackoff.multipliedBy(1L shl minOf(failureCount - 1, MAX_DOUBLINGS))
        return minOf(doubled, settings.maxBackoff)
    }

    private companion object {
        /** Keeps the shift from overflowing; the cap applies long before this. */
        const val MAX_DOUBLINGS = 20
    }
}
