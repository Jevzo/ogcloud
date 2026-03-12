package io.ogwars.cloud.common.event

data class TemplatePushEvent(
    val serverId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
