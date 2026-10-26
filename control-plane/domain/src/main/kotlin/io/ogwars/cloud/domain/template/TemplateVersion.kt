package io.ogwars.cloud.domain.template

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import java.time.Instant
import java.util.UUID

enum class TemplateSource {
    UPLOAD,

    /** Pushed back from a running server (D-0042). */
    PUSH,

    /** Built from an imported modpack. */
    IMPORT,
}

/** An immutable template archive in object storage. */
data class TemplateVersion(
    val id: UUID,
    val groupId: GroupId,
    val objectKey: String,
    val sha256: String,
    val sizeBytes: Long,
    val source: TemplateSource,
    val createdBy: Actor,
    val createdAt: Instant,
) {
    init {
        require(SHA256.matches(sha256)) { "sha256 must be 64 lowercase hex characters" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
    }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
