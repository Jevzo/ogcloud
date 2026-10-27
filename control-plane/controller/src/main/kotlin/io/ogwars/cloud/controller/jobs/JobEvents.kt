package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.contracts.events.v1.JobProgress
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.livestate.toProto
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import org.springframework.stereotype.Component

/** `ogcloud.job.progress` for the API and the dashboards. */
fun interface JobEvents {
    fun progress(job: Job)
}

@Component
class KafkaJobEvents(
    private val publisher: EventPublisher,
    private val metadata: EventMetadataFactory,
) : JobEvents {
    override fun progress(job: Job) {
        val event =
            JobProgress
                .newBuilder()
                .setMetadata(metadata.create(job.createdBy))
                .setJobId(job.id.toString())
                .setState(job.state.toProto())
                .setCompletedSteps(job.completedSteps)
                .setTotalSteps(job.totalSteps)
                .setMessage(job.message)
                .build()
        publisher.publish(KafkaTopics.JOB_PROGRESS, job.id.toString(), event)
    }
}
