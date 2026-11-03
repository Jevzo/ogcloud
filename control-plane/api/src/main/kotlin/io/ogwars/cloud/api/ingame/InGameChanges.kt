package io.ogwars.cloud.api.ingame

import io.ogwars.cloud.messaging.NonRetryableEventException

/**
 * Applies a change an in-game admin made (D-0129, D-0132). The gateway already checked the admin; a change the API
 * refuses, e.g. for a malformed id or a missing group, can never succeed, so it is dead-lettered without retries.
 */
inline fun applyOrRefuse(
    eventId: String,
    apply: () -> Unit,
) {
    try {
        apply()
    } catch (e: IllegalArgumentException) {
        throw NonRetryableEventException("In-game change $eventId refused: ${e.message}", e)
    } catch (e: NoSuchElementException) {
        throw NonRetryableEventException("In-game change $eventId refused: ${e.message}", e)
    }
}
