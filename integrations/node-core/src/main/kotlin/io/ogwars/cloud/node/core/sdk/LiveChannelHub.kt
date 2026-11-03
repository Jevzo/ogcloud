package io.ogwars.cloud.node.core.sdk

import io.ogwars.cloud.contracts.LiveChannelLimits
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.LiveChannelPublish
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.sdk.LiveChannels
import io.ogwars.cloud.sdk.LiveMessage
import io.ogwars.cloud.sdk.Subscription
import com.google.protobuf.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Live channels over the gateway session (D-0116): at most once, so a publish while reconnecting is dropped. The
 * gateway's limits are checked here first, so plugin authors see a mistake at once.
 */
internal class LiveChannelHub(
    private val session: NodeSession,
) : LiveChannels {
    private val log = System.getLogger(LiveChannelHub::class.java.name)
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<LiveMessage>>>()

    override fun publish(
        channel: String,
        typeId: String,
        payload: ByteArray,
    ) {
        checkChannel(channel)
        require(typeId.length <= LiveChannelLimits.MAX_TYPE_ID_LENGTH) {
            "typeId is longer than ${LiveChannelLimits.MAX_TYPE_ID_LENGTH}"
        }
        require(payload.size <= LiveChannelLimits.MAX_PAYLOAD_BYTES) {
            "payload is larger than ${LiveChannelLimits.MAX_PAYLOAD_BYTES} bytes"
        }
        val publish =
            LiveChannelPublish
                .newBuilder()
                .setChannel(channel)
                .setTypeId(typeId)
                .setPayload(ByteString.copyFrom(payload))
        if (!session.send(SessionRequest.newBuilder().setLivePublish(publish).build())) {
            log.log(System.Logger.Level.DEBUG, "Dropped a publish to {0}: not connected", channel)
        }
    }

    override fun subscribe(
        channel: String,
        listener: Consumer<LiveMessage>,
    ): Subscription {
        checkChannel(channel)
        var first = false
        listeners.compute(channel) { _, held ->
            first = held == null
            check(!first || listeners.size < LiveChannelLimits.MAX_SUBSCRIPTIONS) {
                "A server subscribes to at most ${LiveChannelLimits.MAX_SUBSCRIPTIONS} channels"
            }
            (held ?: CopyOnWriteArrayList()).apply { add(listener) }
        }
        if (first) session.subscribe(channel)
        return Subscription { unsubscribe(channel, listener) }
    }

    /** Hands a message to the channel's listeners; one listener's failure does not stop the others. */
    fun deliver(message: LiveChannelDelivery) {
        val view = MessageView(message.channel, message.senderServerId, message.typeId, message.payload.toByteArray())
        listeners[message.channel]?.forEach { listener ->
            runCatching { listener.accept(view) }.onFailure {
                log.log(System.Logger.Level.WARNING, "A live-channel listener on ${message.channel} failed", it)
            }
        }
    }

    private fun unsubscribe(
        channel: String,
        listener: Consumer<LiveMessage>,
    ) {
        var last = false
        listeners.computeIfPresent(channel) { _, held ->
            held.remove(listener)
            last = held.isEmpty()
            if (last) null else held
        }
        if (last) session.unsubscribe(channel)
    }

    private fun checkChannel(channel: String) =
        require(CHANNEL.matches(channel)) { "Channel '$channel' must be 1 to 64 of a-z 0-9 . _ : -" }

    private class MessageView(
        override val channel: String,
        override val senderServerId: String,
        override val typeId: String,
        override val payload: ByteArray,
    ) : LiveMessage

    private companion object {
        val CHANNEL = Regex(LiveChannelLimits.CHANNEL_PATTERN)
    }
}
