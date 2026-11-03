package io.ogwars.cloud.sdk

import java.util.function.Consumer

/**
 * Network-wide publish and subscribe for plugins: at most once, up to 64 KiB, no history. A message reaches every
 * subscriber, the sender's server included. [LiveMessage.typeId] tells the payload's format apart.
 */
interface LiveChannels {
    /** Channel names are 1 to 64 of `a-z 0-9 . _ : -`. */
    fun publish(
        channel: String,
        typeId: String,
        payload: ByteArray,
    )

    /** [listener] runs off the server thread. */
    fun subscribe(
        channel: String,
        listener: Consumer<LiveMessage>,
    ): Subscription
}

interface LiveMessage {
    val channel: String
    val senderServerId: String
    val typeId: String
    val payload: ByteArray
}

/** Ends a subscription. */
fun interface Subscription : AutoCloseable {
    override fun close()
}
