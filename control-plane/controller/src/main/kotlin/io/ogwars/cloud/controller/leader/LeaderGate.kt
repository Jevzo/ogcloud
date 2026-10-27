package io.ogwars.cloud.controller.leader

import io.ogwars.cloud.controller.config.ControllerProperties
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Campaigns for the controller Lease and runs the [LeaderLoop]s and [LeaderService]s only while this replica leads. A replica that
 * loses the Lease stops its loops and campaigns again; stale writes are fenced by versions (D-0069).
 */
@Component
class LeaderGate(
    private val client: KubernetesClient,
    private val properties: ControllerProperties,
    private val loops: List<LeaderLoop>,
    private val services: List<LeaderService>,
    private val meters: MeterRegistry,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val leading = AtomicBoolean(false)
    private val lock = Any()
    private var election: CompletableFuture<*>? = null
    private var scheduler: ScheduledExecutorService? = null

    init {
        Gauge.builder(LEADER_METRIC, leading) { if (it.get()) 1.0 else 0.0 }.register(meters)
    }

    val isLeader: Boolean get() = leading.get()

    override fun start() {
        running.set(true)
        campaign()
    }

    override fun stop() {
        running.set(false)
        // Cancelling releases the Lease, so a standby takes over without waiting for it to expire.
        synchronized(lock) { election?.cancel(true) }
        stopLeading()
    }

    override fun isRunning(): Boolean = running.get()

    private fun campaign() {
        val election = properties.leaderElection
        val config =
            LeaderElectionConfigBuilder()
                .withName(election.leaseName)
                .withLock(LeaseLock(properties.namespace, election.leaseName, properties.identity))
                .withLeaseDuration(election.leaseDuration)
                .withRenewDeadline(election.renewDeadline)
                .withRetryPeriod(election.retryPeriod)
                .withReleaseOnCancel(true)
                .withLeaderCallbacks(
                    LeaderCallbacks(::startLeading, ::onLeadershipLost) { log.info("Controller leader is {}", it) },
                ).build()
        synchronized(lock) {
            this.election =
                client
                    .leaderElector()
                    .withConfig(config)
                    .build()
                    .start()
        }
    }

    private fun startLeading() {
        synchronized(lock) {
            log.info("{} leads; starting {} loops", properties.identity, loops.size)
            val executor =
                Executors.newScheduledThreadPool(
                    maxOf(loops.size, 1),
                    Thread.ofVirtual().name("leader-loop-", 0).factory(),
                )
            loops.forEach { loop ->
                executor.scheduleWithFixedDelay(
                    { runGuarded(loop) },
                    0,
                    loop.interval.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
            }
            scheduler = executor
            services.forEach { it.startLeading() }
            leading.set(true)
        }
    }

    private fun onLeadershipLost() {
        stopLeading()
        if (running.get()) {
            log.warn("{} lost the controller Lease; campaigning again", properties.identity)
            campaign()
        }
    }

    private fun stopLeading() {
        synchronized(lock) {
            if (leading.getAndSet(false)) services.forEach { it.stopLeading() }
            scheduler?.let { executor ->
                executor.shutdownNow()
                if (!executor.awaitTermination(LOOP_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.error("Leader loops did not stop within {} s", LOOP_STOP_TIMEOUT_SECONDS)
                }
            }
            scheduler = null
        }
    }

    // A loop that throws would never be scheduled again, so failures are logged, counted and retried next interval.
    private fun runGuarded(loop: LeaderLoop) {
        try {
            loop.runOnce()
        } catch (e: Exception) {
            // Losing the Lease interrupts running loops; that is not a failure of the loop.
            if (!leading.get()) {
                log.debug("Leader loop {} stopped with the leadership", loop.name, e)
                return
            }
            log.error("Leader loop {} failed; retrying in {}", loop.name, loop.interval, e)
            Counter
                .builder(LOOP_FAILURES_METRIC)
                .tag("loop", loop.name)
                .register(meters)
                .increment()
        }
    }

    private companion object {
        const val LEADER_METRIC = "ogcloud.controller.leader"
        const val LOOP_FAILURES_METRIC = "ogcloud.controller.loop.failures"
        const val LOOP_STOP_TIMEOUT_SECONDS = 10L
    }
}
