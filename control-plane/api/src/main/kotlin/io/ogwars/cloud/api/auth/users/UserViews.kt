package io.ogwars.cloud.api.auth.users

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
    val createdAt: Instant,
    /** Present it when updating; a stale one is refused with 409 (D-0069). */
    val version: Long,
)

fun Revisioned<WebUser>.toView() =
    UserView(
        value.id,
        value.username,
        value.email,
        value.roleId,
        value.enabled,
        value.linkedPlayerUuid,
        value.createdAt,
        revision,
    )

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val roleId: String,
)

data class UpdateUserRequest(
    val username: String,
    val email: String,
    val roleId: String,
    val enabled: Boolean,
    val version: Long,
)

data class PasswordRequest(
    val password: String,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class MeView(
    val id: UUID,
    val username: String,
    val email: String,
    val roleId: String,
    /** What the user may do now; empty while a required Minecraft link is missing (D-0094). */
    val permissions: List<String>,
    val linkedPlayerUuid: UUID?,
)

data class SetupStatus(
    val pending: Boolean,
)

data class SetupRequest(
    val setupToken: String,
    val username: String,
    val email: String,
    val password: String,
)
