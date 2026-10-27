package io.ogwars.cloud.domain.job

/** Job kinds the API creates and the controller runs (D-0039); payload keys are listed with each kind. */
object JobKinds {
    /** Payload: `groupId`, optional `batchSize`. The group must be in maintenance. */
    const val GROUP_RESTART = "group.restart"

    /** Payload: optional `batchSize`. Network maintenance must be on. */
    const val NETWORK_RESTART = "network.restart"

    /** Payload: `groupId`. The API soft-deletes the group; the job stops its servers. */
    const val GROUP_DELETE = "group.delete"

    /** No payload. Rotates the BungeeGuard token (D-0065, D-0078). */
    const val FORWARDING_ROTATE = "forwarding.rotate"

    /** Payload: `profileId`. Replaces the servers of a profile whose runtime bundle changed (D-0079). */
    const val BUNDLE_ROLLOUT = "bundle.rollout"

    const val GROUP_ID = "groupId"
    const val BATCH_SIZE = "batchSize"

    /** Servers replaced at once when the payload sets no [BATCH_SIZE]. */
    const val DEFAULT_BATCH_SIZE = 5
    const val PROFILE_ID = "profileId"

    /** Jobs that replace servers; only one runs at a time, the rest wait queued. */
    val EXCLUSIVE = setOf(GROUP_RESTART, NETWORK_RESTART, FORWARDING_ROTATE, BUNDLE_ROLLOUT)
}
