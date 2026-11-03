package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.Welcome
import java.util.concurrent.CompletableFuture

/** What the gateway sends a node. Callbacks run on gRPC's threads and must not block. */
interface SessionListener {
    /** The session is (again) open; [welcome] holds the current state to start from. */
    fun welcomed(welcome: Welcome)

    fun lifecycle(event: ServerLifecycleChanged)

    fun broadcast(event: NodeBroadcast)

    /** Acts on a delivery; the gateway learns the outcome once the future completes (D-0116). */
    fun deliver(delivery: Delivery): CompletableFuture<DeliveryOutcome>

    fun liveMessage(message: LiveChannelDelivery)

    /** The session ended; the client reconnects on its own until closed. */
    fun disconnected(cause: Throwable?)
}

/** Whether a delivery worked, with a word for the gateway's logs. */
data class DeliveryOutcome(
    val success: Boolean,
    val message: String = "",
)
