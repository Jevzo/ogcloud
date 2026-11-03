package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.node.v1.AdminRequest
import io.ogwars.cloud.contracts.node.v1.AdminRequestResult
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import java.util.concurrent.CompletableFuture

/** A node's gateway session; [NodeClient] is the real one. */
interface NodeSession : AutoCloseable {
    /** Opens the session; it reconnects on its own until closed. */
    fun start()

    /** Sends [request] if a session is open; false when it is not, e.g. while reconnecting. */
    fun send(request: SessionRequest): Boolean

    /** Completes with the gateway's result, or fails with [NotConnectedException] when the session drops first. */
    fun serverCommand(command: ServerCommandRequest.Builder): CompletableFuture<ServerCommandResult>

    /** An in-game admin's change (D-0132), answered like [serverCommand]. */
    fun adminRequest(request: AdminRequest.Builder): CompletableFuture<AdminRequestResult>

    /** Live channels stay subscribed across reconnects. */
    fun subscribe(channel: String)

    fun unsubscribe(channel: String)
}
