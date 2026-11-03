package io.ogwars.cloud.paper

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Messages
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID

class PaperAdapterTest {
    private val server = MockBukkit.mock()
    private val plugin = MockBukkit.createMockPlugin()
    private val steve = server.addPlayer("Steve")
    private val admins =
        PermissionGroup
            .newBuilder()
            .setPermissionGroupId("admin")
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

    @AfterEach
    fun unmock() = MockBukkit.unmock()

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

        PaperChat(runtime).onChat(event)

        val rendered = event.renderer().render(steve, steve.displayName(), event.message(), steve)
        PlainTextComponentSerializer.plainText().serialize(rendered) shouldBe "[Admin] Steve: &ahi"
    }

    @Test
    fun `teams get the group's prefix as a component and its name colour`() {
        val team = server.scoreboardManager.newScoreboard.registerNewTeam("t")

        PaperLine(plugin).styleTeam(team, admins)

        team.prefix() shouldBe Component.text("", NamedTextColor.RED)
        team.color() shouldBe NamedTextColor.RED
    }
}
