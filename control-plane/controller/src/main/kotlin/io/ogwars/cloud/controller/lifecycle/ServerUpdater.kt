package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.store.ServerStateStore
import java.util.UUID

data class ServerChange(
    val before: Server,
    val after: Server,
)

/** Read, change and compare-and-set; another replica's write in between triggers a retry with fresh state. */
class ServerUpdater(
    private val servers: ServerStateStore,
) {
    /** Applies [change]; returns null when the server is gone or [change] returned null (nothing to do). */
    fun update(
        id: UUID,
        change: (Server) -> Server?,
    ): ServerChange? {
        repeat(MAX_ATTEMPTS) {
            val current = servers.get(id) ?: return null
            val updated = change(current.value) ?: return null
            if (servers.compareAndSet(current.revision, updated)) return ServerChange(current.value, updated)
        }
        throw IllegalStateException("Server $id kept changing; gave up after $MAX_ATTEMPTS attempts")
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
