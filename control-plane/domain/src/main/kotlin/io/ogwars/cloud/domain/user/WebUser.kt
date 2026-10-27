package io.ogwars.cloud.domain.user

import java.time.Instant
import java.util.UUID

/** Someone who signs in to the dashboard, desktop or mobile app (D-0012). */
data class WebUser(
    val id: UUID,
    val username: String,
    val email: String,
    /** Encoded with its `{id}` prefix, e.g. `{bcrypt}`; null for passkey-only accounts. */
    val passwordHash: String?,
    val roleId: String,
    val createdAt: Instant,
    val linkedPlayerUuid: UUID? = null,
    val enabled: Boolean = true,
) {
    init {
        require(username.length in USERNAME_LENGTH) {
            "Username must be ${USERNAME_LENGTH.first} to ${USERNAME_LENGTH.last} characters"
        }
        require(email.contains('@')) { "Invalid email address" }
    }

    private companion object {
        val USERNAME_LENGTH = 3..32
    }
}
