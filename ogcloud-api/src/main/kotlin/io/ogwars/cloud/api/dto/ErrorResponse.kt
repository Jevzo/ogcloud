package io.ogwars.cloud.api.dto

data class ErrorResponse(
    val status: Int,
    val message: String,
    val details: List<String> = emptyList(),
)
