package io.ogwars.cloud.legacy

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerLoginEvent
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

    @Test
    fun `team prefixes end with the name colour and fit 1_8's 16 characters`() {
        LegacyLine.teamPrefix(admins.toBuilder().setTabPrefix("<gray>[VIP] ").build()) shouldBe "§7[VIP] §c"
        LegacyLine.teamPrefix(admins) shouldBe "§c§l[Administrat"
        LegacyLine.teamPrefix(admins.toBuilder().setTabPrefix("<red>[Admin]123456").build()) shouldBe "§c[Admin]123456"
    }

    @Test
    fun `gradients in a team prefix take their first colour, so it fits (D-0204)`() {
        val vip =
            admins
                .toBuilder()
                .setTabPrefix("<gradient:#ff5555:#5555ff>[VIP]</gradient> ")
                .setNameFormat("<gradient:#55ff55:#5555ff>{name}</gradient>")
                .build()
        LegacyLine.teamPrefix(vip) shouldBe "§c[VIP]§r §a"
    }

    @Test
    fun `chat puts the rank's head in the format and keeps the typed message as it is`() {
        val runtime =
            mockk<ServerRuntime> {
                every { messages } returns this@LegacyRulesTest.messages
                every { network.settings.permissionSystemEnabled } returns true
                every { permissions.rank(steve.uniqueId) } returns Rank(PlayerProfile.getDefaultInstance(), admins)
            }
        val event = AsyncPlayerChatEvent(true, steve, "100% &ahi", mutableSetOf())

        LegacyChat(runtime).onChat(event)

        // The catalog's suffix is <gray>: <white>, so the message continues in white.
        event.format shouldBe "§c§l[Admin] §cSteve§7: §f§r§f%2\$s"
        String.format(event.format, "Steve", event.message).endsWith("100% &ahi") shouldBe true
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
