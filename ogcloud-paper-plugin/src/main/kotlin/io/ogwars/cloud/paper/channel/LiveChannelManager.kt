package io.ogwars.cloud.paper.channel

import io.ogwars.cloud.common.channel.LiveChannelEnvelope
import io.ogwars.cloud.common.channel.LiveChannelNodeType
import io.ogwars.cloud.common.channel.LiveChannelPayload
import io.ogwars.cloud.common.channel.LiveChannelSender
import io.ogwars.cloud.common.channel.LiveChannelSubscription
import io.ogwars.cloud.common.channel.LiveChannelTypeIds
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.paper.kafka.KafkaSendDispatcher
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

class LiveChannelManager(
    private val serverId: String,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val logger: Logger,
) {
    private val gson = Gson()
    private val nextSubscriptionId = AtomicLong(1)
    private val subscriptionsByChannel =
        ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredSubscription<out LiveChannelPayload>>>()

    fun <T : LiveChannelPayload> subscribe(
        channelName: String,
        payloadType: Class<T>,
        listener: Consumer<T>,
    ): LiveChannelSubscription {
        requireChannelName(channelName)

        val subscription =
            RegisteredSubscription(
                id = nextSubscriptionId.getAndIncrement(),
                payloadType = payloadType,
                listener = listener,
            )

        subscriptionsByChannel.computeIfAbsent(channelName) { CopyOnWriteArrayList() }.add(subscription)

        return SubscriptionHandle(
            channelName = channelName,
            subscriptionId = subscription.id,
        )
    }

    fun <T : LiveChannelPayload> publish(
        channelName: String,
        payload: T,
    ) {
        requireChannelName(channelName)

        val envelope =
            LiveChannelEnvelope(
                channelName = channelName,
                sender =
                    LiveChannelSender(
                        nodeId = serverId,
                        nodeType = LiveChannelNodeType.PAPER,
                    ),
                publishedAtEpochMillis = System.currentTimeMillis(),
                payloadType = LiveChannelTypeIds.payloadTypeId(payload),
                payloadJson = gson.toJson(payload),
            )

        kafkaSendDispatcher.dispatch(
            KafkaSendDispatcher.Message(
                topic = KafkaTopics.LIVE_CHANNEL,
                key = channelName,
                payload = gson.toJson(envelope),
                type = KafkaSendDispatcher.MessageType.LIVE_CHANNEL,
            ),
        )
    }

    fun handleIncoming(rawEnvelope: String) {
        val envelope =
            gson.fromJson(rawEnvelope, LiveChannelEnvelope::class.java)
                ?: throw IllegalArgumentException("Live channel envelope must not be null")
        validateEnvelope(envelope)

        val channelSubscriptions = subscriptionsByChannel[envelope.channelName] ?: return
        if (channelSubscriptions.isEmpty()) {
            return
        }

        channelSubscriptions
            .groupBy { it.payloadTypeId }
            .forEach { (expectedPayloadType, subscriptions) ->
                if (expectedPayloadType != envelope.payloadType) {
                    logger.warning(
                        "Dropped live channel payload due to type mismatch: " +
                            "channel=${envelope.channelName}, senderNodeId=${envelope.sender.nodeId}, " +
                            "senderNodeType=${envelope.sender.nodeType}, expectedPayloadType=$expectedPayloadType, " +
                            "actualPayloadType=${envelope.payloadType}, subscriberCount=${subscriptions.size}",
                    )
                    return@forEach
                }

                val decodedPayload = decodePayload(envelope, subscriptions.first()) ?: return@forEach
                subscriptions.forEach { subscription ->
                    deliverPayload(subscription, decodedPayload, envelope)
                }
            }
    }

    private fun validateEnvelope(envelope: LiveChannelEnvelope) {
        requireChannelName(envelope.channelName)
        require(envelope.sender.nodeId.isNotBlank()) { "Live channel sender node id must not be blank" }
        require(envelope.payloadType.isNotBlank()) { "Live channel payload type must not be blank" }
    }

    private fun requireChannelName(channelName: String) {
        require(channelName.isNotBlank()) { "Live channel name must not be blank" }
    }

    private fun decodePayload(
        envelope: LiveChannelEnvelope,
        subscription: RegisteredSubscription<out LiveChannelPayload>,
    ): LiveChannelPayload? =
        try {
            subscription.payloadType.cast(gson.fromJson(envelope.payloadJson, subscription.payloadType))
        } catch (exception: Exception) {
            logger.log(
                Level.WARNING,
                "Dropped live channel payload due to decode failure: " +
                    "channel=${envelope.channelName}, senderNodeId=${envelope.sender.nodeId}, " +
                    "senderNodeType=${envelope.sender.nodeType}, payloadType=${envelope.payloadType}",
                exception,
            )
            null
        }

    private fun deliverPayload(
        subscription: RegisteredSubscription<out LiveChannelPayload>,
        payload: LiveChannelPayload,
        envelope: LiveChannelEnvelope,
    ) {
        try {
            subscription.deliver(payload)
        } catch (exception: Exception) {
            logger.log(
                Level.SEVERE,
                "Live channel listener failed: channel=${envelope.channelName}, " +
                    "senderNodeId=${envelope.sender.nodeId}, senderNodeType=${envelope.sender.nodeType}, " +
                    "payloadType=${envelope.payloadType}, subscriptionId=${subscription.id}",
                exception,
            )
        }
    }

    private fun unsubscribe(
        channelName: String,
        subscriptionId: Long,
    ) {
        subscriptionsByChannel.computeIfPresent(channelName) { _, current ->
            current.removeIf { it.id == subscriptionId }
            if (current.isEmpty()) {
                null
            } else {
                current
            }
        }
    }

    private data class RegisteredSubscription<T : LiveChannelPayload>(
        val id: Long,
        val payloadType: Class<T>,
        val listener: Consumer<T>,
    ) {
        val payloadTypeId: String = LiveChannelTypeIds.payloadTypeId(payloadType)

        fun deliver(payload: LiveChannelPayload) {
            listener.accept(payloadType.cast(payload))
        }
    }

    private inner class SubscriptionHandle(
        private val channelName: String,
        private val subscriptionId: Long,
    ) : LiveChannelSubscription {
        private val active = AtomicBoolean(true)

        override fun unsubscribe() {
            if (!active.compareAndSet(true, false)) {
                return
            }

            unsubscribe(channelName, subscriptionId)
        }
    }
}
