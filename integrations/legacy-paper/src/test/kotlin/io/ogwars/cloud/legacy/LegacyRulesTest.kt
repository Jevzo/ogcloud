package io.ogwars.cloud.legacy

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.NamedColor
import io.ogwars.cloud.text.RichText
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.scoreboard.Team
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.util.UUID

class LegacyRulesTest {
    private val messages = Messages.load()
    private val steve =
        mockk<Player> {
            every { uniqueId } returns UUID.randomUUID()
            every { name } returns "Steve"
        }
    private val admins =
        PermissionGroup
            .newBuilder()
            .setPermissionGroupId("admin")
            .setChatPrefix("<red><bold>[Admin] ")
            .setNameFormat("<red>{name}")
            .setTabPrefix("<red><bold>[Administrator] ")
            .build()

    private fun teamPrefix(
        tabPrefix: String,
        nameColor: Color?,
    ): String {
        val prefix = slot<String>()
        val team = mockk<Team> { every { setPrefix(capture(prefix)) } just Runs }
        LegacyLine(mockk()).styleTeam(team, RichText.parseValid(tabPrefix), nameColor)
        return prefix.captured
    }

    @Test
    fun `team prefixes end with the name's colour and fit 1_8's 16 characters, cut before it`() {
        teamPrefix("<gray>[VIP] ", NamedColor.RED) shouldBe "§7[VIP] §c"
        teamPrefix("<red><bold>[Administrator] ", NamedColor.RED) shouldBe "§c§l[Administr§c"
        teamPrefix("<red>[Admin]123456", NamedColor.RED) shouldBe "§c[Admin]12345§c"
        teamPrefix("<red>[Admin]123456", null) shouldBe "§c[Admin]123456"
    }

    @Test
    fun `gradients in a team prefix take their first colour, so it fits (D-0204)`() {
        teamPrefix("<gradient:#ff5555:#5555ff>[VIP]</gradient> ", Color.Hex(0x55ff55)) shouldBe "§c[VIP]§r §a"
    }

    @Test
    fun `chat puts the rank's head in the format and keeps the typed message as it is`() {
        val runtime =
            mockk<ServerRuntime> {
                every { messages } returns this@LegacyRulesTest.messages
                every { texts } returns StoredTexts()
                every { network.settings.permissionSystemEnabled } returns true
                every { permissions.rank(steve.uniqueId) } returns Rank(PlayerProfile.getDefaultInstance(), admins)
            }
        val event = AsyncPlayerChatEvent(true, steve, "100% <green>hi", mutableSetOf())

        LegacyChat(runtime).onChat(event)

        // The catalog's suffix is <gray>: <white>, so the message continues in white.
        event.format shouldBe "§c§l[Admin] §cSteve§7: §r§f%2\$s"
        String.format(event.format, "Steve", event.message).endsWith("100% <green>hi") shouldBe true
    }

    @Test
    fun `logins beyond the group's players per server are refused, below it the server's own limit is lifted`() {
        val online = mutableListOf<Player>()
        val server = mockk<Server> { every { onlinePlayers } answers { online } }
        val limit = PlayerLimit(server, 2, messages)

        fun login(serverSays: PlayerLoginEvent.Result) =
            PlayerLoginEvent(steve, "mc.example", InetAddress.getLoopbackAddress()).apply {
                result = serverSays
                limit.onLogin(this)
            }

        online += steve
        login(PlayerLoginEvent.Result.KICK_FULL).result shouldBe PlayerLoginEvent.Result.ALLOWED
        online += steve
        val refused = login(PlayerLoginEvent.Result.ALLOWED)
        refused.result shouldBe PlayerLoginEvent.Result.KICK_FULL
        refused.kickMessage shouldBe "§cThis server is full."
    }
}
