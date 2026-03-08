package io.ogwars.cloud.api.dto

import jakarta.validation.constraints.NotBlank

data class CommandExecuteRequest(
    @field:NotBlank val target: String,
    @field:NotBlank val targetType: String,
    @field:NotBlank val command: String,
)
