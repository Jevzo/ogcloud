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
        // Sign-in takes a username or an email, so a username must never look like someone's email.
        require('@' !in username) { "Usernames cannot contain @" }
        require(email.contains('@')) { "Invalid email address" }
    }

    val isOwner: Boolean get() = roleId == BuiltInRole.OWNER.id

    companion object {
        val USERNAME_LENGTH = 3..32
    }
}

/** A named set of permissions; built-in roles keep theirs in code (D-0089). */
data class Role(
    val id: String,
    val name: String,
    val permissions: Set<ApiPermission>,
    val builtIn: Boolean = false,
) {
    init {
        require(ID.matches(id)) { "Role id must be lowercase letters, digits and dashes, starting with a letter" }
        require(name.isNotBlank()) { "Role name must not be blank" }
        require(
            builtIn == (BuiltInRole.find(id) != null),
        ) { "'$id' is ${if (builtIn) "not" else "already"} a built-in role" }
    }

    private companion object {
        val ID = Regex("^[a-z][a-z0-9-]{0,30}$")
    }
}

/** Length is all that counts (NIST SP 800-63B); bcrypt reads at most 72 bytes (D-0094). */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_BYTES = 72

    fun validate(password: String) {
        require(
            password.codePointCount(0, password.length) >= MIN_LENGTH,
        ) { "Passwords need at least $MIN_LENGTH characters" }
        require(password.toByteArray().size <= MAX_BYTES) { "Passwords may have at most $MAX_BYTES bytes" }
    }
}
