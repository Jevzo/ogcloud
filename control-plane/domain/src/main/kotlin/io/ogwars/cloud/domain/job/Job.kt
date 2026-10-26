package io.ogwars.cloud.domain.job

import io.ogwars.cloud.domain.audit.Actor
import java.time.Instant
import java.util.UUID

enum class JobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    val isFinished: Boolean get() = this == SUCCEEDED || this == FAILED
}

/** A long-running operation executed step by step by the controller leader (D-0039). */
data class Job(
    val id: UUID,
    val kind: String,
    val state: JobState,
    val payload: Map<String, String>,
    /** Where a new leader resumes. */
    val progress: Map<String, String>,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String,
    val createdBy: Actor,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
)
