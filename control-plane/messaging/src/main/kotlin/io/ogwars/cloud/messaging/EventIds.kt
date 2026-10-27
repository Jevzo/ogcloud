package io.ogwars.cloud.messaging

import java.util.UUID

/** Parses an id field of an event; a malformed id can never succeed, so it is not retried. */
fun String.toUuid(field: String): UUID =
    try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw NonRetryableEventException("Invalid $field '$this'", e)
    }
