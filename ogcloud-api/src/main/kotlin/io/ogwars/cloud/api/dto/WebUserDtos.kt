package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.WebUserDocument
import io.ogwars.cloud.api.model.WebUserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateWebUserRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val role: String,
)

data class UpdateWebUserRequest(
    @field:Email
    val email: String? = null,
    @field:Pattern(regexp = ".*\\S.*", message = "must not be blank")
    val password: String? = null,
    @field:Pattern(regexp = ".*\\S.*", message = "must not be blank")
    val username: String? = null,
    val role: String? = null,
)

data class WebUserResponse(
    val id: String,
    val email: String,
    val username: String,
    val role: WebUserRole,
    val linkedPlayerUuid: String?,
)

fun WebUserDocument.toResponse(): WebUserResponse =
    WebUserResponse(
        id = id,
        email = email,
        username = username,
        role = role,
        linkedPlayerUuid = linkedPlayerUuid,
    )

fun UpdateWebUserRequest.parseRole(): WebUserRole? = role?.let(WebUserRole::parse)
