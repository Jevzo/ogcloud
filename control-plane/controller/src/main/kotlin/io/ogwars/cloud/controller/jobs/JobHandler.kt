package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.job.Job

/**
 * Runs one kind of job one step per call. A new leader may repeat the last step, so steps are idempotent and
 * everything needed to resume lives in [Job.progress] (D-0039).
 */
interface JobHandler {
    val kind: String

    fun step(job: Job): JobStep
}

sealed interface JobStep {
    data class Continue(
        val progress: Map<String, String>,
        val completedSteps: Int,
        val totalSteps: Int,
        val message: String,
    ) : JobStep

    data class Succeeded(
        val message: String,
    ) : JobStep

    data class Failed(
        val message: String,
    ) : JobStep
}

/** Comma-separated lists in a job's progress map. */
internal fun Map<String, String>.list(key: String): List<String> =
    this[key]?.split(',')?.filter(String::isNotEmpty).orEmpty()

internal fun List<String>.joinedList(): String = joinToString(",")
