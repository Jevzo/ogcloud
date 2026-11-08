package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.format.Tablist
import io.ogwars.cloud.node.core.permission.Decision
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Messages
import com.destroystokyo.paper.event.player.PlayerHandshakeEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.Component
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.logging.Logger

/** The shared code is built against the 1.8 API and runs here on Paper's, the newest line it must work on. */
class BackendListenersTest {
    private val server = MockBukkit.mock()
    private val steve = server.addPlayer("Steve")
    private val admins =
        PermissionGroup
            .newBuilder()
            .setPermissionGroupId("admin")
            .setWeight(900)
            .setTabPrefix("&c[Admin] ")
            .addPermissions("vip.fly")
            .build()
    private val ranks = mutableMapOf<UUID, Rank>()
    private val runtime =
        mockk<ServerRuntime>(relaxed = true) {
            every { messages } returns Messages.load()
            every { network.settings.permissionSystemEnabled } returns true
            every { permissions.rank(any()) } answers { ranks[firstArg()] }
            every { permissions.decide(any(), any(), any()) } answers {
                if (ranks[firstArg()]?.grants(secondArg()) == true) Decision.ALLOW else Decision.DENY
            }
        }
    private val tablist = TeamTablist(server, InlineLine()) { ranks[it] }
    private val listeners =
        BackendListeners(
            runtime,
            ForwardingTokens(Path.of("tokens"), Clock.systemUTC()) { "known\n" },
            tablist,
            Logger.getAnonymousLogger(),
        )

    @AfterEach
    fun unmock() = MockBukkit.unmock()

    @Test
    fun `forwarded logins with a known token fill in the player, others fail`() {
        val player = UUID.randomUUID()
        val accepted = PlayerHandshakeEvent(handshake(player, "known"), "10.0.0.5", false)
        val forged = PlayerHandshakeEvent(handshake(player, "forged"), "10.0.0.5", false)

        listeners.onHandshake(accepted)
        listeners.onHandshake(forged)

        // The server uses the event's data only while it is not cancelled; otherwise it parses the raw
        // handshake itself, token included.
        accepted.isCancelled shouldBe false
        accepted.isFailed shouldBe false
        accepted.uniqueId shouldBe player
        accepted.socketAddressHostname shouldBe "203.0.113.7"
        forged.isFailed shouldBe true
    }

    @Test
    fun `a handshake that cannot be checked fails instead of passing unchecked`() {
        val player = UUID.randomUUID()
        val nullName =
            PlayerHandshakeEvent(handshake(player, "known").replace("\"bungeeguard-token\"", "null"), "10.0.0.5", false)
        val unreadable =
            BackendListeners(
                runtime,
                ForwardingTokens(Path.of("tokens"), Clock.systemUTC()) { throw java.io.IOException("not mounted") },
                tablist,
                Logger.getAnonymousLogger(),
            )
        val noTokens = PlayerHandshakeEvent(handshake(player, "known"), "10.0.0.5", false)

        listeners.onHandshake(nullName)
        unreadable.onHandshake(noTokens)

        nullName.isFailed shouldBe true
        noTokens.isFailed shouldBe true
    }

    @Test
    fun `a joining player holds OgCloud's nodes on top of Bukkit's and is announced`() {
        ranks[steve.uniqueId] = Rank(PlayerProfile.getDefaultInstance(), admins)

        listeners.onJoin(PlayerJoinEvent(steve, Component.empty()))

        steve.hasPermission("vip.fly") shouldBe true
        steve.hasPermission("vip.build") shouldBe false
        verify { runtime.joined(steve.uniqueId) }
    }

    @Test
    fun `what decided a player's permissions before OgCloud still does, including later attachments`() {
        val plugin = MockBukkit.createMockPlugin()
        // Another permission plugin's grant, made before OgCloud injects at join.
        steve.addAttachment(plugin, "other.plugin", true)

        PermissionInjector.inject(steve) { _, node -> if (node == "vip.fly") Decision.ALLOW else Decision.DEFAULT }
        steve.addAttachment(plugin, "later.grant", true)

        steve.hasPermission("vip.fly") shouldBe true
        steve.hasPermission("other.plugin") shouldBe true
        steve.hasPermission("later.grant") shouldBe true
        steve.hasPermission("nobody.granted") shouldBe false
    }

    @Test
    fun `tablist teams follow ranks and leave with the player`() {
        val alex = server.addPlayer("Alex")
        ranks[steve.uniqueId] = Rank(PlayerProfile.getDefaultInstance(), admins)
        tablist.enable(true)

        val team = alex.scoreboard.getEntryTeam("Steve")
        team?.name shouldBe Tablist.teamName(admins)
        @Suppress("DEPRECATION")
        team?.prefix shouldBe "§c[Admin] "
        alex.scoreboard.getEntryTeam("Alex") shouldBe null
        alex.scoreboard shouldNotBe steve.scoreboard

        listeners.onQuit(PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED))
        alex.scoreboard.getEntryTeam("Steve") shouldBe null
        verify { runtime.left(steve.uniqueId) }
    }

    private fun handshake(
        player: UUID,
        token: String,
    ) = listOf(
        "lobby",
        "203.0.113.7",
        player.toString(),
        """[{"name":"bungeeguard-token","value":"$token"}]""",
    ).joinToString("\u0000")
}
