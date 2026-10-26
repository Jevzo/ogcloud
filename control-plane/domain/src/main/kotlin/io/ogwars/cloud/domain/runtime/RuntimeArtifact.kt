package io.ogwars.cloud.domain.runtime

import java.time.Instant

/** A runtime-bundle file copied to object storage for one profile (T-0209). */
data class RuntimeArtifact(
    val profileId: String,
    /** Path relative to the server directory, e.g. "plugins/ViaVersion.jar". */
    val path: String,
    val objectKey: String,
    val sourceUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val storedAt: Instant,
)
