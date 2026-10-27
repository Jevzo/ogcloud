package io.ogwars.cloud.api.events

import io.ogwars.cloud.api.jobs.JobManagement
import io.ogwars.cloud.contracts.events.v1.AlertRaised
import io.ogwars.cloud.contracts.events.v1.JobProgress
import io.ogwars.cloud.contracts.events.v1.ScalingDecided
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.livestate.toDomain
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.ListenerContainers
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.ogwars.cloud.messaging.protobufContainer
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.springframework.stereotype.Component
import java.util.UUID
import io.ogwars.cloud.contracts.events.v1.AlertSeverity as ProtoAlertSeverity
import io.ogwars.cloud.contracts.events.v1.ServerState as ProtoServerState

/** `server`: a server changed state. */
data class ServerEvent(
    val serverId: UUID,
    val groupId: String,
    val groupType: GroupType,
    val state: ServerState,
    /** Null for a server's first event. */
    val previousState: ServerState?,
    val reason: String,
)

/** `job`: a job made progress (D-0039). */
data class JobEvent(
    val jobId: UUID,
    val state: JobState,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String,
)

/** `scaling`: the autoscaler decided something. */
data class ScalingEvent(
    val groupId: String,
    val action: ScalingAction,
    val serverId: UUID?,
    val reason: String,
)

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

/** `alert`: something needs attention; the apps translate [kind], e.g. `group.crash-loop-paused`. */
data class AlertEvent(
    val subjectId: String,
    val severity: AlertSeverity,
    val kind: String,
    val attributes: Map<String, String>,
)

/**
 * Feeds this replica's live-update streams from Kafka (D-0105): a consumer group of its own per topic sees every
 * record from now on and commits nothing, so it disappears with the replica.
 */
@Component
class LiveEventFeed(
    private val components: KafkaComponents,
    private val events: LiveEvents,
    private val jobs: JobStore,
    ids: IdGenerator,
) : ListenerContainers() {
    private val replica = ids.next()
    override val containers =
        listOf(
            container(KafkaTopics.SERVER_LIFECYCLE, "lifecycle", ServerLifecycleChanged.parser(), ::server),
            container(KafkaTopics.JOB_PROGRESS, "jobs", JobProgress.parser(), ::job),
            container(KafkaTopics.SCALING_DECIDED, "scaling", ScalingDecided.parser(), ::scaling),
            container(KafkaTopics.ALERT_RAISED, "alerts", AlertRaised.parser(), ::alert),
        )

    private fun server(event: ServerLifecycleChanged) =
        events.publish(
            "server",
            ServerEvent(
                UUID.fromString(event.serverId),
                event.groupId,
                event.groupType.toDomain(),
                event.state.toDomain(),
                event.previousState.takeUnless { it == ProtoServerState.SERVER_STATE_UNSPECIFIED }?.toDomain(),
                event.reason,
            ),
            ApiPermission.SERVERS_READ.scope,
        )

    private fun job(event: JobProgress) {
        val id = UUID.fromString(event.jobId)
        // The event carries no kind; a job of an unknown kind stays private to network readers.
        val permission = JobManagement.readPermission(jobs.find(id)?.kind.orEmpty())
        events.publish(
            "job",
            JobEvent(id, event.state.toDomain(), event.completedSteps, event.totalSteps, event.message),
            permission.scope,
        )
    }

    private fun scaling(event: ScalingDecided) =
        events.publish(
            "scaling",
            ScalingEvent(
                event.groupId,
                event.action.toDomain(),
                event.serverId.takeIf(String::isNotEmpty)?.let(UUID::fromString),
                event.reason,
            ),
            ApiPermission.OVERVIEW_READ.scope,
        )

    private fun alert(event: AlertRaised) =
        events.publish(
            "alert",
            AlertEvent(event.subjectId, event.severity.toApi(), event.kind, event.attributesMap),
            ApiPermission.OVERVIEW_READ.scope,
        )

    // Unspecified or unknown values come from a newer or broken producer; the broadcast skips the record.
    private fun ProtoAlertSeverity.toApi(): AlertSeverity =
        AlertSeverity.entries.find { "ALERT_SEVERITY_${it.name}" == name }
            ?: throw NonRetryableEventException("No alert severity for $name")

    private fun <T : Message> container(
        topic: String,
        name: String,
        parser: Parser<T>,
        handle: (T) -> Unit,
    ) = components.broadcastContainerFactory("ogcloud-api-live-$name-$replica").protobufContainer(topic, parser, handle)
}
