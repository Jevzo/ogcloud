package io.ogwars.cloud.velocity

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.node.core.permission.Decision
import io.ogwars.cloud.node.core.routing.Route
import io.ogwars.cloud.node.core.runtime.LoginDecision
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.node.core.state.Backend
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class VelocityAdapterTest {
    private val steve = UUID.randomUUID()
    private val backend =
        Backend(
            "0192d2c1-1a2b-7c3d-8e4f-123456789abc",
            "staff-lobby-789abc",
            "staff-lobby",
            GroupType.GROUP_TYPE_DYNAMIC,
            "10.0.0.7",
            25565,
            50,
            47,
            false,
        )

    @Test
    fun `players report their backend by its id, whatever dashes the group has (v1 bug 2)`() {
        val connection =
            mockk<ServerConnection> {
                every { serverInfo } returns
                    ServerInfo(backend.id, InetSocketAddress(0))
            }
        val player = player(currentServer = connection)
        val proxy = mockk<ProxyServer> { every { allPlayers } returns listOf(player) }

        VelocityPlatform(proxy, via = null).onlinePlayers() shouldContainExactly
            listOf(OnlinePlayer(steve, "Steve", backend.id))
    }

    @Test
    fun `a backend at a new address replaces its registration`() {
        val old =
            mockk<RegisteredServer> {
                every { serverInfo } returns
                    ServerInfo(backend.id, InetSocketAddress("10.0.0.1", 25565))
            }
        val proxy =
            mockk<ProxyServer>(relaxed = true) {
                every { getServer(backend.id) } returns Optional.of(old)
            }

        VelocityPlatform(proxy, via = null).registered(backend)

        verifyOrder {
            proxy.unregisterServer(ServerInfo(backend.id, InetSocketAddress("10.0.0.1", 25565)))
            proxy.registerServer(ServerInfo(backend.id, InetSocketAddress("10.0.0.7", 25565)))
        }
    }

    @Test
    fun `players are sent to registered backends`() {
        val request = mockk<ConnectionRequestBuilder>(relaxed = true)
        val registered = mockk<RegisteredServer>()
        val player =
            player(currentServer = null).also {
                every { it.createConnectionRequest(registered) } returns
                    request
            }
        val proxy =
            mockk<ProxyServer> {
                every { getPlayer(steve) } returns Optional.of(player)
                every { getServer(backend.id) } returns Optional.of(registered)
            }

        VelocityPlatform(proxy, via = null).connect(steve, backend)

        verify { request.fireAndForget() }
    }

    @Test
    fun `OgCloud decides permissions while its system is on, the previous provider otherwise`() {
        val runtime = mockk<ProxyRuntime>()
        every { runtime.network.settings.permissionSystemEnabled } returns true
        every { runtime.permissions.decide(steve, "allowed", true) } returns Decision.ALLOW
        every { runtime.permissions.decide(steve, "unset", true) } returns Decision.DEFAULT
        val player = player(currentServer = null)
        val previous = PermissionProvider { PermissionFunction { Tristate.UNDEFINED } }
        val event = PermissionsSetupEvent(player, previous)

        VelocityListeners(mockk(), runtime).onPermissionsSetup(event)
        val permissions = event.provider.createFunction(player)

        permissions.getPermissionValue("allowed") shouldBe Tristate.TRUE
        permissions.getPermissionValue("unset") shouldBe Tristate.UNDEFINED
    }

    @Test
    fun `players gone after their login completed are reported, abandoned logins only forget their rank`() {
        val runtime = mockk<ProxyRuntime>(relaxed = true)
        val listeners = VelocityListeners(mockk(), runtime)
        val player = mockk<Player> { every { uniqueId } returns steve }

        DisconnectEvent.LoginStatus.entries.forEach { listeners.onDisconnect(DisconnectEvent(player, it)) }

        verify(exactly = 2) { runtime.left(steve) }
        verify(exactly = 3) { runtime.loginAbandoned(steve) }
    }

    @Test
    fun `a denied login is refused with the controller's text, and one refused already is left alone`() {
        val runtime = mockk<ProxyRuntime>()
        val bypass = slot<() -> Boolean>()
        every { runtime.login(steve, "Steve", 47, "1.2.3.4", capture(bypass)) } returns
            CompletableFuture.completedFuture(LoginDecision.Denied("&cMaintenance"))
        val listeners = VelocityListeners(mockk(), runtime)
        val player = player(currentServer = null)
        val event = LoginEvent(player, "")

        listeners.onLogin(event)
        every { player.hasPermission(PermissionNodes.MAINTENANCE_BYPASS) } returns true

        event.result.isAllowed shouldBe false
        event.result.reasonComponent.map { plain(it) } shouldBe Optional.of("Maintenance")
        bypass.captured() shouldBe true
        val refused =
            LoginEvent(
                player,
                "",
            ).apply { result = ResultedEvent.ComponentResult.denied(Component.text("x")) }
        listeners.onLogin(refused) shouldBe null
        verify(exactly = 1) { runtime.login(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed backend sends a joining player elsewhere, never back to it, and tells one already playing`() {
        val runtime = mockk<ProxyRuntime> { every { messages } returns Messages.load() }
        val failed = registered(backend.id)
        val other = registered("other")
        val proxy = mockk<ProxyServer> { every { getServer("other") } returns Optional.of(other) }
        val listeners = VelocityListeners(proxy, runtime)

        fun kicked(
            player: Player,
            reason: Component? = null,
        ) = KickedFromServerEvent(player, failed, reason, true, KickedFromServerEvent.Notify.create(Component.empty()))
        val joining = player(currentServer = null)
        val playing = player(currentServer = mockk())

        val banned = kicked(joining, Component.text("banned")).also(listeners::onKickedFromServer)
        val elsewhere = kicked(playing).also(listeners::onKickedFromServer)
        every { runtime.initialServer(steve) } returns Route.To(backend.copy(id = "other"))
        val redirected = kicked(joining).also(listeners::onKickedFromServer)
        every { runtime.initialServer(steve) } returns Route.To(backend)
        val stranded = kicked(joining).also(listeners::onKickedFromServer)

        (banned.result as KickedFromServerEvent.Notify).messageComponent shouldBe Component.empty()
        plain((elsewhere.result as KickedFromServerEvent.Notify).messageComponent) shouldBe
            plain(legacyText(Messages.load().text(Message.KICK_SERVER_DOWN)))
        (redirected.result as KickedFromServerEvent.RedirectPlayer).server shouldBe other
        redirected.result.shouldBeInstanceOf<KickedFromServerEvent.RedirectPlayer>()
        stranded.result.shouldBeInstanceOf<KickedFromServerEvent.DisconnectPlayer>()
    }

    // Paper checks its limit only at the end of a switch, when the player can no longer go back (D-0142).
    @Test
    fun `a player a full backend turns away mid-switch goes to another backend of its group`() {
        val runtime = mockk<ProxyRuntime>()
        val full = registered(backend.id)
        val other = registered("other")
        val proxy = mockk<ProxyServer> { every { getServer("other") } returns Optional.of(other) }
        val listeners = VelocityListeners(proxy, runtime)
        val switching = player(currentServer = mockk())
        val serverFull = Component.translatable("multiplayer.disconnect.server_full")

        fun turnedAway() =
            KickedFromServerEvent(
                switching,
                full,
                serverFull,
                true,
                KickedFromServerEvent.DisconnectPlayer.create(serverFull),
            ).also(listeners::onKickedFromServer)
        every { runtime.refused(steve, backend.id) } returns Route.To(backend.copy(id = "other"))
        val redirected = turnedAway()
        every { runtime.refused(steve, backend.id) } returns Route.Kick(Message.KICK_NO_SERVERS)
        val stranded = turnedAway()

        (redirected.result as KickedFromServerEvent.RedirectPlayer).server shouldBe other
        (stranded.result as KickedFromServerEvent.DisconnectPlayer).reasonComponent shouldBe serverFull
    }

    @Test
    fun `only the backend's npc command passes through the proxy (v1 bug 4)`() {
        VelocityListeners.isBackendCommand("ogcloud npc list") shouldBe true
        VelocityListeners.isBackendCommand("OC npc") shouldBe true
        VelocityListeners.isBackendCommand("ogcloud server list") shouldBe false
        VelocityListeners.isBackendCommand("ogcloud npcs") shouldBe false
    }

    @Test
    fun `a bare command completes from an empty word`() {
        OgCloudCommand.words(emptyArray()) shouldContainExactly listOf("")
        OgCloudCommand.words(arrayOf("group", "")) shouldContainExactly listOf("group", "")
    }

    private fun player(currentServer: ServerConnection?) =
        mockk<Player>(relaxed = true) {
            every { uniqueId } returns steve
            every { username } returns "Steve"
            every { protocolVersion } returns ProtocolVersion.MINECRAFT_1_8
            every { remoteAddress } returns InetSocketAddress("1.2.3.4", 1)
            every { this@mockk.currentServer } returns Optional.ofNullable(currentServer)
        }

    private fun registered(name: String) =
        mockk<RegisteredServer> { every { serverInfo } returns ServerInfo(name, InetSocketAddress(0)) }

    private fun plain(text: Component) = PlainTextComponentSerializer.plainText().serialize(text)
}
