package io.ogwars.cloud.api.event

data class WebAccountLinkOtpEvent(
    val playerUuid: String,
    val otp: String,
    val requestedByEmail: String,
    val timestamp: Long = System.currentTimeMillis()
)
