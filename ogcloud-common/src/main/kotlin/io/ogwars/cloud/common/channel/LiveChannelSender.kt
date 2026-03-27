package io.ogwars.cloud.common.channel

data class LiveChannelSender(
    val nodeId: String,
    val nodeType: LiveChannelNodeType,
)
