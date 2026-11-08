package io.ogwars.cloud.velocity

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.node.core.permission.Decision
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.node.core.state.Backend
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.Optional
import java.util.UUID

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
            every { this@mockk.currentServer } returns Optional.ofNullable(currentServer)
        }
}
