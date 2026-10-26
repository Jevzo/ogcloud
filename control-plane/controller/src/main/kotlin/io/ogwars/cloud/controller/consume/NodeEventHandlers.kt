package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.GameStateChanged
import io.ogwars.cloud.contracts.events.v1.NodeHeartbeat
import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.events.v1.PlayerActivity.ActivityCase
import io.ogwars.cloud.contracts.toInstant
import io.ogwars.cloud.controller.lifecycle.LifecycleService
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.livestate.toDomain
import io.ogwars.cloud.messaging.NonRetryableEventException
import org.springframework.stereotype.Component

/** `ogcloud.node.heartbeat`: player counts and liveness; the first heartbeat makes a server RUNNING. */
@Component
class HeartbeatHandler(
    private val lifecycle: LifecycleService,
) {
    fun handle(heartbeat: NodeHeartbeat) {
        lifecycle.heartbeat(
            heartbeat.serverId.toUuid("server_id"),
            heartbeat.players,
            heartbeat.maxPlayers,
            heartbeat.metadata.occurredAt.toInstant(),
        )
    }
}

/** `ogcloud.server.gamestate` from DYNAMIC servers. */
@Component
class GameStateHandler(
    private val lifecycle: LifecycleService,
    private val network: NetworkSettingsStore,
) {
    fun handle(event: GameStateChanged) {
        val state =
            event.gameState.toDomain()
                ?: throw NonRetryableEventException("Game state of ${event.serverId} is unspecified")
        lifecycle.gameStateChanged(event.serverId.toUuid("server_id"), state, network.load().value.defaultGroupId)
    }
}

/**
 * `ogcloud.player.activity`: keeps sessions on the right server. Login resolution writes the session on connect
 * (T-0207); a disconnect removes it only if it still belongs to that proxy, so a reconnect elsewhere survives (D-0069).
 */
@Component
class PlayerActivityHandler(
    private val sessions: SessionStore,
) {
    fun handle(activity: PlayerActivity) {
        val player = activity.playerUuid.toUuid("player_uuid")
        when (activity.activityCase) {
            ActivityCase.SWITCHED -> {
                val target = activity.switched.toServerId.toUuid("to_server_id")
                repeat(MAX_ATTEMPTS) {
                    val session = sessions.get(player) ?: return
                    if (session.value.serverId == target) return
                    if (sessions.compareAndSet(session.revision, session.value.copy(serverId = target))) return
                }
                throw IllegalStateException("Session of $player kept changing; retrying the switch")
            }

            ActivityCase.DISCONNECTED -> {
                val proxy = activity.disconnected.proxyId.toUuid("proxy_id")
                val session = sessions.get(player) ?: return
                if (session.value.proxyId == proxy) sessions.remove(session)
            }

            ActivityCase.CONNECTED -> {}

            ActivityCase.ACTIVITY_NOT_SET, null -> {
                throw NonRetryableEventException("Activity of $player carries no activity")
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
