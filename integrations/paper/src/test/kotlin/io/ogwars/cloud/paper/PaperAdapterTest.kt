package io.ogwars.cloud.paper

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.RichText
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
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
            .setChatPrefix("<red>[Admin] ")
            .setNameFormat("<gradient:#ff5555:#5555ff>{name}</gradient>")
            .setTabPrefix("<gradient:#000000:#ffffff>[A]</gradient>")
            .build()
    private val ranks = mutableMapOf<UUID, Rank>()
    private val runtime =
        mockk<ServerRuntime> {
            every { messages } returns Messages.load()
            every { texts } returns StoredTexts()
            every { network.settings.permissionSystemEnabled } returns true
            every { permissions.rank(any()) } answers { ranks[firstArg()] }
        }

    @AfterEach
    fun unmock() = MockBukkit.unmock()

    private fun chat(
        typed: Component,
        suffix: String? = null,
    ): Component {
        val group = suffix?.let { admins.toBuilder().setChatSuffix(it).build() } ?: admins
        ranks[steve.uniqueId] = Rank(PlayerProfile.getDefaultInstance(), group)
        val event =
            AsyncChatEvent(
                false,
                steve,
                mutableSetOf(),
                ChatRenderer.defaultRenderer(),
                typed,
                typed,
                mockk<SignedMessage>(),
            )
        PaperChat(runtime).onChat(event)
        return event.renderer().render(steve, steve.displayName(), event.message(), steve)
    }

    @Test
    fun `chat shows the rank's head and keeps typed tags literal`() {
        val rendered = chat(Component.text("<green>hi"))
        PlainTextComponentSerializer.plainText().serialize(rendered) shouldBe "[Admin] Steve: <green>hi"
    }

    @Test
    fun `the typed message continues in the head's end colour unless it has one of its own`() {
        // The catalog's suffix is <gray>: <white>.
        chat(Component.text("hi")).children().last().color() shouldBe NamedTextColor.WHITE
        chat(Component.text("hi"), suffix = " <#123456>» ").children().last().color() shouldBe TextColor.color(0x123456)
        chat(Component.text("hi"), suffix = " <gray>»</gray> ").children().last().color() shouldBe null
        chat(Component.text("hi", NamedTextColor.AQUA)).children().last().color() shouldBe NamedTextColor.AQUA
    }

    @Test
    fun `teams get the group's prefix as a component and the nearest named colour of the name's`() {
        val team = server.scoreboardManager.newScoreboard.registerNewTeam("t")
        val line = PaperLine(plugin)

        line.styleTeam(team, RichText.parseValid(admins.tabPrefix), Color.Hex(0xff5555))

        // The prefix keeps its hex colours; the team colour is a named one (D-0206).
        team.prefix() shouldBe
            Component
                .text()
                .append(Component.text("[", TextColor.color(0x000000)))
                .append(Component.text("A", TextColor.color(0x808080)))
                .append(Component.text("]", TextColor.color(0xffffff)))
                .build()
        team.color() shouldBe NamedTextColor.RED
        line.styleTeam(team, RichText.plain(""), null)
        team.color() shouldBe NamedTextColor.WHITE
    }
}
