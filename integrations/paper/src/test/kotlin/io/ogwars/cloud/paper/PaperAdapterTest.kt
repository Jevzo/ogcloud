package io.ogwars.cloud.paper

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
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.logging.Logger

class PaperAdapterTest {
    private val server = MockBukkit.mock()
    private val steve = server.addPlayer("Steve")
    private val admins =
        PermissionGroup
            .newBuilder()
            .setPermissionGroupId("admin")
            .setWeight(900)
            .setChatPrefix("&c[Admin] ")
            .setNameColor("&c")
            .setTabPrefix("&c")
            .build()
    private val ranks = mutableMapOf<UUID, Rank>()
    private val runtime =
        mockk<ServerRuntime> {
            every { messages } returns Messages.load()
            every { network.settings.permissionSystemEnabled } returns true
            every { permissions.rank(any()) } answers { ranks[firstArg()] }
        }
    private val tablist = TeamTablist(server) { ranks[it] }
    private val listeners =
        PaperListeners(
            runtime,
            ForwardingTokens(Path.of("tokens"), Clock.systemUTC()) {
                "known\n"
            },
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

        accepted.isCancelled shouldBe true
        accepted.uniqueId shouldBe player
        accepted.socketAddressHostname shouldBe "203.0.113.7"
        forged.isFailed shouldBe true
    }

    @Test
    fun `an injected player holds OgCloud's nodes on top of Bukkit's`() {
        PermissionInjector.inject(steve) { _, node -> if (node == "vip.fly") Decision.ALLOW else Decision.DENY }

        steve.hasPermission("vip.fly") shouldBe true
        steve.hasPermission("vip.build") shouldBe false
    }

    @Test
    fun `chat shows the rank's head and keeps typed colour codes literal`() {
        ranks[steve.uniqueId] = Rank(PlayerProfile.getDefaultInstance(), admins)
        val event =
            AsyncChatEvent(
                false,
                steve,
                mutableSetOf(),
                ChatRenderer.defaultRenderer(),
                Component.text("&ahi"),
                Component.text("&ahi"),
                mockk<SignedMessage>(),
            )

        listeners.onChat(event)

        val rendered = event.renderer().render(steve, steve.displayName(), event.message(), steve)
        PlainTextComponentSerializer.plainText().serialize(rendered) shouldBe "[Admin] Steve: &ahi"
    }

    @Test
    fun `tablist teams follow ranks, heaviest first`() {
        val alex = server.addPlayer("Alex")
        ranks[steve.uniqueId] = Rank(PlayerProfile.getDefaultInstance(), admins)
        tablist.enable(true)

        alex.scoreboard.getEntryTeam("Steve")?.name shouldBe
            io.ogwars.cloud.node.core.format.Tablist
                .teamName(admins)
        alex.scoreboard.getEntryTeam("Alex") shouldBe null
        alex.scoreboard shouldNotBe steve.scoreboard

        tablist.remove(steve)
        alex.scoreboard.getEntryTeam("Steve") shouldBe null
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
