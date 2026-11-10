package io.ogwars.cloud.velocity

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.node.core.permission.Decision
import io.ogwars.cloud.node.core.routing.Route
import io.ogwars.cloud.node.core.runtime.LoginDecision
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.node.core.text.Message
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.command.CommandExecuteEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.TranslatableComponent

/** Velocity's events, handed to node-core's proxy runtime. */
internal class VelocityListeners(
    private val proxy: ProxyServer,
    private val runtime: ProxyRuntime,
) {
    /** Waits for the controller's verdict (maintenance, rank) without holding a Velocity thread. */
    @Subscribe
    fun onLogin(event: LoginEvent): EventTask? {
        if (!event.result.isAllowed) return null
        val player = event.player
        val address = player.remoteAddress.address.hostAddress
        val login =
            runtime.login(player.uniqueId, player.username, player.protocolVersion.protocol, address) {
                player.hasPermission(PermissionNodes.MAINTENANCE_BYPASS)
            }
        val decided =
            login.thenAccept { decision ->
                if (decision is LoginDecision.Denied) {
                    event.result = ResultedEvent.ComponentResult.denied(legacyText(decision.message))
                }
            }
        return EventTask.resumeWhenComplete(decided)
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        runtime.joined(player.uniqueId, player.username, player.protocolVersion.protocol)
    }

    @Subscribe
    fun onChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        when (val route = runtime.initialServer(event.player.uniqueId)) {
            is Route.To -> proxy.getServer(route.backend.id).ifPresent(event::setInitialServer)
            is Route.Kick -> event.player.disconnect(text(route.reason))
            Route.NoRoom -> event.player.disconnect(text(Message.KICK_NO_SERVERS))
        }
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val from = event.previousServer.map { it.serverInfo.name }.orElse(null)
        runtime.switched(event.player.uniqueId, from, event.server.serverInfo.name)
    }

    /**
     * Only players who completed the login were reported, so only they are reported gone; an abandoned login still
     * drops the rank it loaded. A conflicting login leaves the live player with the same id alone.
     */
    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        when (event.loginStatus) {
            in REPORTED_LOGINS -> runtime.left(event.player.uniqueId)
            in ABANDONED_LOGINS -> runtime.loginAbandoned(event.player.uniqueId)
            else -> Unit
        }
    }

    /**
     * As v1: a backend that kicks with a reason has the last word. One that just failed sends a player still
     * joining to another default-group server, and tells a player already playing elsewhere. A full backend that
     * turns away a player who has no server to stay on, as Paper does at the end of a switch, sends them to another
     * backend of its group instead (D-0142).
     */
    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        if (event.player.currentServer.isEmpty && event.result is KickedFromServerEvent.DisconnectPlayer &&
            isFull(event)
        ) {
            val instead = runtime.refused(event.player.uniqueId, event.server.serverInfo.name)
            val server = (instead as? Route.To)?.let { proxy.getServer(it.backend.id).orElse(null) }
            if (server != null) {
                event.result = KickedFromServerEvent.RedirectPlayer.create(server)
                return
            }
        }
        if (event.serverKickReason.isPresent) return
        if (event.player.currentServer.isPresent) {
            event.result = KickedFromServerEvent.Notify.create(text(Message.KICK_SERVER_DOWN))
            return
        }
        val route = runtime.initialServer(event.player.uniqueId)
        val fallback = (route as? Route.To)?.backend?.takeIf { it.id != event.server.serverInfo.name }
        event.result =
            fallback
                ?.let { proxy.getServer(it.id).orElse(null) }
                ?.let { KickedFromServerEvent.RedirectPlayer.create(it) }
                ?: KickedFromServerEvent.DisconnectPlayer.create(text(Message.KICK_NO_SERVERS))
    }

    private fun isFull(event: KickedFromServerEvent): Boolean =
        (event.serverKickReason.orElse(null) as? TranslatableComponent)?.key() == SERVER_FULL

    /** OgCloud decides players' permissions while its permission system is on; otherwise the previous provider does. */
    @Subscribe
    fun onPermissionsSetup(event: PermissionsSetupEvent) {
        val player = event.subject as? Player ?: return
        val previous = event.provider
        event.provider =
            PermissionProvider { subject ->
                val fallback = previous.createFunction(subject)
                PermissionFunction { node ->
                    val enabled = runtime.network.settings.permissionSystemEnabled
                    when (runtime.permissions.decide(player.uniqueId, node, enabled)) {
                        Decision.ALLOW -> Tristate.TRUE
                        Decision.DENY -> Tristate.FALSE
                        Decision.DEFAULT -> fallback.getPermissionValue(node)
                    }
                }
            }
    }

    /** `/ogcloud npc` belongs to the backend's plugin, so the proxy passes it on (v1 bug 4). */
    @Subscribe
    fun onCommand(event: CommandExecuteEvent) {
        if (isBackendCommand(event.command)) event.result = CommandExecuteEvent.CommandResult.forwardToServer()
    }

    private fun text(message: Message) = legacyText(runtime.messages.text(message))

    companion object {
        // Vanilla's reason when a server is at its player limit.
        private const val SERVER_FULL = "multiplayer.disconnect.server_full"

        private val REPORTED_LOGINS =
            setOf(DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN, DisconnectEvent.LoginStatus.PRE_SERVER_JOIN)
        private val ABANDONED_LOGINS =
            setOf(
                DisconnectEvent.LoginStatus.CANCELLED_BY_USER,
                DisconnectEvent.LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE,
                DisconnectEvent.LoginStatus.CANCELLED_BY_PROXY,
            )
        private val BACKEND_COMMAND = Regex("^(ogcloud|oc)\\s+npc(\\s.*)?$", RegexOption.IGNORE_CASE)

        /** Whether a command line, without its slash, is a backend's `/ogcloud npc ...`. */
        fun isBackendCommand(command: String): Boolean = BACKEND_COMMAND.matches(command.trim())
    }
}
