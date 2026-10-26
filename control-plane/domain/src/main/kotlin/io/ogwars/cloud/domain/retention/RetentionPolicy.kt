package io.ogwars.cloud.domain.retention

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Data kinds deleted once they are older than their retention (D-0043). */
enum class RetentionKind {
    AUDIT_LOG,
    SCALING_LOG,
    FINISHED_JOB,

    /** Tokens, OTPs and push registrations not used for this long. */
    STALE_CREDENTIAL,
}

data class RetentionSettings(
    val templateVersionsToKeep: Int = 3,
    val deletedGroupGrace: Duration = Duration.ofDays(7),
    val ages: Map<RetentionKind, Duration> =
        mapOf(
            RetentionKind.AUDIT_LOG to Duration.ofDays(90),
            RetentionKind.SCALING_LOG to Duration.ofDays(30),
            RetentionKind.FINISHED_JOB to Duration.ofDays(30),
            RetentionKind.STALE_CREDENTIAL to Duration.ofDays(30),
        ),
) {
    init {
        require(templateVersionsToKeep >= 0) { "templateVersionsToKeep must not be negative" }
        require(RetentionKind.entries.all { it in ages }) { "Every retention kind needs an age" }
    }
}

data class TemplateVersion(
    val id: UUID,
    val createdAt: Instant,
)

/** Decides what the cleanup loop may delete; it never deletes anything still referenced. */
class RetentionPolicy(
    private val settings: RetentionSettings = RetentionSettings(),
) {
    /** Keeps the active version plus the newest [RetentionSettings.templateVersionsToKeep] others. */
    fun templateVersionsToDelete(
        versions: List<TemplateVersion>,
        activeVersionId: UUID?,
    ): List<TemplateVersion> =
        versions
            .filter { it.id != activeVersionId }
            .sortedByDescending(TemplateVersion::createdAt)
            .drop(settings.templateVersionsToKeep)

    /** The instant before which records of [kind] are deleted. */
    fun cutoff(
        kind: RetentionKind,
        now: Instant,
    ): Instant = now.minus(settings.ages.getValue(kind))

    fun isGroupPurgeDue(
        deletedAt: Instant,
        now: Instant,
    ): Boolean = !now.isBefore(deletedAt.plus(settings.deletedGroupGrace))
}
