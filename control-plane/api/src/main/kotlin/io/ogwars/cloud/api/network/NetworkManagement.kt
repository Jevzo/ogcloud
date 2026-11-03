package io.ogwars.cloud.api.network

import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.api.groups.batchPayload
import io.ogwars.cloud.api.jobs.JobManagement
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.retryOnStale
import org.springframework.stereotype.Service
import java.time.Clock

/** Network-wide settings, the Minecraft EULA (D-0046, D-0100) and network-wide jobs. */
@Service
class NetworkManagement(
    private val network: NetworkSettingsStore,
    private val owners: Owners,
    private val jobs: JobManagement,
    private val announcements: Announcements,
    private val clock: Clock,
) {
    fun get(): Revisioned<NetworkSettings> = network.load()

    /** The EULA acceptance is kept as stored; only [recordEula] sets it, for an owner or the chart (D-0100). */
    fun update(
        actor: Actor,
        expectedVersion: Long,
        settings: NetworkSettings,
    ): Revisioned<NetworkSettings> {
        network.save(expectedVersion, settings.copy(eulaAcceptedAt = network.load().value.eulaAcceptedAt))
        announcements.networkSettings(actor)
        return get()
    }

    /** A network owner accepts the Minecraft EULA; servers start only afterwards (D-0046). */
    fun acceptEula(actor: Actor): Revisioned<NetworkSettings> {
        owners.requireOwner(actor)
        recordEula(actor)
        return get()
    }

    /** Records the acceptance once; concurrent recorders, e.g. API replicas starting, settle on the first. */
    fun recordEula(actor: Actor) =
        change(actor) { if (it.eulaAccepted) null else it.copy(eulaAcceptedAt = clock.instant()) }

    /** Turns network maintenance on or off, e.g. for an in-game admin (D-0132). */
    fun setMaintenance(
        actor: Actor,
        enabled: Boolean,
    ) = change(actor) { if (it.maintenance == enabled) null else it.copy(maintenance = enabled) }

    /** Applies [edit] on top of concurrent changes; an edit returning null leaves the settings as they are. */
    private fun change(
        actor: Actor,
        edit: (NetworkSettings) -> NetworkSettings?,
    ) {
        retryOnStale(ATTEMPTS) {
            val current = network.load()
            edit(current.value)?.let {
                network.save(current.revision, it)
                announcements.networkSettings(actor)
            }
            Unit
        } ?: throw ConflictException("The network settings kept changing; try again")
    }

    /** Replaces every server, proxies first; like v1, only during network maintenance. */
    fun restart(
        actor: Actor,
        batchSize: Int?,
    ): Job {
        if (!get().value.maintenance) {
            throw ConflictException(
                "Turn on network maintenance before restarting the network",
            )
        }
        return jobs.start(actor, JobKinds.NETWORK_RESTART, batchPayload(batchSize))
    }

    /** Rotates the BungeeGuard token through the controller (D-0065, D-0078). */
    fun rotateForwardingToken(actor: Actor): Job = jobs.start(actor, JobKinds.FORWARDING_ROTATE)

    private companion object {
        const val ATTEMPTS = 3
    }
}
