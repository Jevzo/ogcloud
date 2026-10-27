package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ogcloud.controller")
data class ControllerProperties(
    /** The platform namespace, which holds the controller's Lease. */
    val namespace: String,
    /** Unique per replica; the pod name in the cluster. */
    val identity: String,
    val leaderElection: LeaderElectionProperties = LeaderElectionProperties(),
)

/** Defaults keep a failover under 15 s (lease plus one retry, T-0609). */
data class LeaderElectionProperties(
    val leaseName: String = "ogcloud-controller",
    val leaseDuration: Duration = Duration.ofSeconds(10),
    val renewDeadline: Duration = Duration.ofSeconds(8),
    val retryPeriod: Duration = Duration.ofSeconds(2),
    /** How soon a leader retries starting its services (informers) after a failure. */
    val serviceRetry: Duration = Duration.ofSeconds(5),
) {
    init {
        require(renewDeadline < leaseDuration) { "renewDeadline must be shorter than leaseDuration" }
        require(retryPeriod < renewDeadline) { "retryPeriod must be shorter than renewDeadline" }
    }
}
