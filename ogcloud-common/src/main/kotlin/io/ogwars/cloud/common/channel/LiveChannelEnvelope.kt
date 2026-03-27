package io.ogwars.cloud.common.channel

data class LiveChannelEnvelope(
    val channelName: String,
    val sender: LiveChannelSender,
    val publishedAtEpochMillis: Long,
    val payloadType: String,
    val payloadJson: String,
)
