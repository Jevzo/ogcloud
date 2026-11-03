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
            .setChatPrefix("&c&l[Admin] ")
            .setNameColor("&c")
            .setTabPrefix("&c&l[Administrator] ")
            .build()

    @Test
    fun `team prefixes end with the name colour and fit 1_8's 16 characters`() {
        LegacyLine.teamPrefix(admins.toBuilder().setTabPrefix("&7[VIP] ").build()) shouldBe "§7[VIP] §c"
        LegacyLine.teamPrefix(admins) shouldBe "§c§l[Administrat"
        LegacyLine.teamPrefix(admins.toBuilder().setTabPrefix("&c[Admin]123456").build()) shouldBe "§c[Admin]123456"
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

        val suffix = messages.text(Message.CHAT_SUFFIX)
        event.format shouldBe "§c§l[Admin] §cSteve${suffix.replace('&', '§')}§r§${suffix.last()}%2\$s"
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
