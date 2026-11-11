package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.auth.ActiveLogin
import io.ogwars.cloud.domain.common.REDACTED
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.user.WebUser
import java.time.Instant
import java.util.UUID

data class UserView(
    val id: UUID,
    val username: String,
    val email: String,
    val roleId: String,
    val enabled: Boolean,
    val linkedPlayerUuid: UUID?,
    /** The linked account's current Minecraft name (D-0106). */
    val linkedPlayerName: String?,
    val createdAt: Instant,
    /** Present it when updating; a stale one is refused with 409 (D-0069). */
    val version: Long,
)

fun Revisioned<WebUser>.toView(linkedPlayerName: String?) =
    UserView(
        value.id,
        value.username,
        value.email,
        value.roleId,
        value.enabled,
        value.linkedPlayerUuid,
        linkedPlayerName,
        value.createdAt,
        revision,
    )

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val roleId: String,
) {
    override fun toString() = "CreateUserRequest(username=$username, email=$email, password=$REDACTED, roleId=$roleId)"
}

data class UpdateUserRequest(
    val username: String,
    val email: String,
    val roleId: String,
    val enabled: Boolean,
    val version: Long,
)

data class PasswordRequest(
    val password: String,
) {
    override fun toString() = "PasswordRequest(password=$REDACTED)"
}

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
) {
    override fun toString() = "ChangePasswordRequest(currentPassword=$REDACTED, newPassword=$REDACTED)"
}

data class MeView(
    val id: UUID,
    val username: String,
    val email: String,
    val roleId: String,
    /** What the user may do now; empty while a required Minecraft link is missing (D-0094). */
    val permissions: List<String>,
    val linkedPlayerUuid: UUID?,
    val linkedPlayerName: String?,
    val totpEnabled: Boolean,
)

data class PasskeyRename(
    val label: String,
)

data class PasskeyRemoval(
    val password: String,
) {
    override fun toString() = "PasskeyRemoval(password=$REDACTED)"
}

/** One of the caller's sign-ins; [current] marks the one whose token made this request. */
data class LoginView(
    val id: String,
    val clientId: String,
    val signedInAt: Instant?,
    val lastActiveAt: Instant?,
    val expiresAt: Instant?,
    val current: Boolean,
) {
    companion object {
        fun of(
            login: ActiveLogin,
            current: Boolean,
        ) = LoginView(login.id, login.clientId, login.signedInAt, login.lastActiveAt, login.expiresAt, current)
    }
}

data class TotpCodeRequest(
    val code: String,
)

data class SetupStatus(
    val pending: Boolean,
)

data class SetupRequest(
    val setupToken: String,
    val username: String,
    val email: String,
    val password: String,
) {
    override fun toString() = "SetupRequest(setupToken=$REDACTED, username=$username, email=$email, password=$REDACTED)"
}

data class MinecraftLinkRequest(
    val playerName: String,
)

data class MinecraftLinkConfirmation(
    val code: String,
)
