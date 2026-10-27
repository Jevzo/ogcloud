package io.ogwars.cloud.api.events

import io.ogwars.cloud.contracts.NETWORK_SUBJECT
import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.contracts.events.v1.EntityChanged
import io.ogwars.cloud.contracts.events.v1.JobRequested
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.PublishException
import com.google.protobuf.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Tells the controller about changes already committed to PostgreSQL. A lost announcement is logged, not
 * surfaced: the change is saved, and the controller's resync and job polling catch up (D-0039, D-0100).
 */
@Component
class Announcements(
    private val publisher: EventPublisher,
    private val metadata: EventMetadataFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun group(
        actor: Actor,
        id: GroupId,
        deleted: Boolean = false,
    ) = configChanged(actor, id.value) { setGroup(entity(id.value, deleted)) }

    fun networkSettings(actor: Actor) =
        configChanged(actor, NETWORK_SUBJECT) {
            setNetworkSettings(entity(NETWORK_SUBJECT))
        }

    fun job(
        actor: Actor,
        jobId: UUID,
    ) = publish(
        KafkaTopics.JOB_REQUESTED,
        jobId.toString(),
        JobRequested
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .setJobId(jobId.toString())
            .build(),
    )

    private fun configChanged(
        actor: Actor,
        subjectId: String,
        change: ConfigChanged.Builder.() -> Unit,
    ) = publish(
        KafkaTopics.CONFIG_CHANGED,
        subjectId,
        ConfigChanged
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .setSubjectId(subjectId)
            .apply(change)
            .build(),
    )

    private fun entity(
        id: String,
        deleted: Boolean = false,
    ) = EntityChanged
        .newBuilder()
        .setId(id)
        .setDeleted(deleted)
        .build()

    private fun publish(
        topic: String,
        key: String,
        event: Message,
    ) {
        try {
            publisher.publishAndWait(topic, key, event)
        } catch (e: PublishException) {
            log.error("Announcing {} {} failed; the controller's resync will pick the change up", topic, key, e)
        }
    }
}
