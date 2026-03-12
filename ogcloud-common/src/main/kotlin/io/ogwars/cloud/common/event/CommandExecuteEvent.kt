package io.ogwars.cloud.common.event

data class CommandExecuteEvent(
    val target: String,
    val targetType: String,
    val command: String,
    val timestamp: Long = System.currentTimeMillis(),
)
