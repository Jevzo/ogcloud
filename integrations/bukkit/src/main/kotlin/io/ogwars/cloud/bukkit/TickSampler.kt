package io.ogwars.cloud.bukkit

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Ticks per second and milliseconds per tick over the last [WINDOW] ticks, for heartbeats. Both Paper and
 * PandaSpigot fire [ServerTickEndEvent], so one measure serves both. [nanoTime] is the monotonic clock.
 */
class TickSampler(
    private val nanoTime: () -> Long = System::nanoTime,
) : Listener {
    private val ends = LongArray(WINDOW)
    private val durations = DoubleArray(WINDOW)
    private var count = 0
    private var next = 0

    @EventHandler(priority = EventPriority.MONITOR)
    fun onTickEnd(event: ServerTickEndEvent) = record(event.tickDuration)

    @Synchronized
    fun record(durationMillis: Double) {
        ends[next] = nanoTime()
        durations[next] = durationMillis
        next = (next + 1) % WINDOW
        if (count < WINDOW) count++
    }

    /** Measured up to now, not the last tick, so a hung main thread shows as falling TPS in the heartbeats. */
    @Synchronized
    fun tps(): Double {
        if (count < 2) return MAX_TPS
        val oldest = ends[if (count < WINDOW) 0 else next]
        val seconds = (nanoTime() - oldest) / NANOS_PER_SECOND
        return if (seconds <= 0) MAX_TPS else minOf(MAX_TPS, (count - 1) / seconds)
    }

    @Synchronized
    fun mspt(): Double = if (count == 0) 0.0 else durations.take(count).average()

    private companion object {
        const val WINDOW = 100
        const val MAX_TPS = 20.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
