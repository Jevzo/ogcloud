package io.ogwars.cloud.api.security

import io.ogwars.cloud.api.model.WebUserRole

data class AuthenticatedUser(
    val id: String,
    val email: String,
    val role: WebUserRole,
)
