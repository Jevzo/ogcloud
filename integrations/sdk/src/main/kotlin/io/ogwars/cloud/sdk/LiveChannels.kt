package io.ogwars.cloud.sdk

import java.util.function.Consumer

/**
 * Network-wide publish and subscribe for plugins: at most once, up to 64 KiB, no history. A message reaches every
 * subscriber, the sender's server included. [LiveMessage.typeId] tells the payload's format apart.
 */
interface LiveChannels {
    /**
     * Channel names are 1 to 64 of `a-z 0-9 . _ : -`, type ids at most 256 characters. A bad argument throws
     * IllegalArgumentException; a publish while this node is not connected is dropped.
     */
    fun publish(
        channel: String,
        typeId: String,
        payload: ByteArray,
    )

    /** [listener] runs off the server thread, one message at a time. Past 64 channels this throws IllegalStateException. */
    fun subscribe(
        channel: String,
        listener: Consumer<LiveMessage>,
    ): Subscription
}

interface LiveMessage {
    val channel: String
    val senderServerId: String
    val typeId: String

    /** A fresh copy on every call, so listeners cannot change each other's bytes. */
    fun payload(): ByteArray
}

fun interface Subscription : AutoCloseable {
    override fun close()
}
