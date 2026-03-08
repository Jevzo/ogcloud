package io.ogwars.cloud.api.event

data class TemplatePushEvent(
    val serverId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
