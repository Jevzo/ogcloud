package io.ogwars.cloud.common.channel

object LiveChannelTypeIds {
    fun payloadTypeId(payload: LiveChannelPayload): String = payload.javaClass.name

    fun payloadTypeId(payloadType: Class<out LiveChannelPayload>): String = payloadType.name
}
