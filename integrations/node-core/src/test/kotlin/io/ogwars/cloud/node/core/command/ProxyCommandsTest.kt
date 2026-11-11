package io.ogwars.cloud.node.core.command

import io.ogwars.cloud.contracts.NodeErrors
import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.node.v1.AdminRequestResult
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.PlayerLocation
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.contracts.toTimestamp
import io.ogwars.cloud.node.core.query.FakeQueries
import io.ogwars.cloud.node.core.runtime.FakeProxyPlatform
import io.ogwars.cloud.node.core.runtime.FakeSession
import io.ogwars.cloud.node.core.runtime.ManualScheduler
import io.ogwars.cloud.node.core.runtime.NodeIdentity
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.text.RichText
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import io.ogwars.cloud.sdk.GroupType as SdkGroupType

class ProxyCommandsTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val messages = Messages.load()
    private val queries = FakeQueries()
    private lateinit var session: FakeSession
    private val runtime =
        ProxyRuntime(
            NodeIdentity("p-1", "proxy", SdkGroupType.PROXY, 100),
            FakeProxyPlatform(),
            queries,
            messages,
            ManualScheduler().executor,
            Runnable::run,
            Clock.fixed(now, ZoneOffset.UTC),
        ) { listener -> FakeSession(listener).also { session = it } }
    private val steve = UUID.randomUUID()
    private val admin = Admin(UUID.randomUUID())

    private class Admin(
        override val player: UUID?,
    ) : CommandSender {
        val replies = mutableListOf<RichText>()

        override fun hasPermission(node: String) = true

        override fun reply(text: RichText) {
            replies += text
        }
    }

    init {
        runtime.start()
        session.listener.welcomed(Welcome.getDefaultInstance())
        queries.servers +=
            ServerInfo
                .newBuilder()
                .setServerId("s-1")
                .setDisplayName("lobby-789abc")
                .setGroupId("lobby")
                .setState(ServerState.SERVER_STATE_RUNNING)
                .setPlayers(3)
                .setMaxPlayers(50)
                .build()
        queries.groups +=
            GroupInfo
                .newBuilder()
                .setGroupId("bedwars")
                .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
                .build()
        queries.players +=
            PlayerLocation
                .newBuilder()
                .setPlayerUuid(
                    steve.toString(),
                ).setPlayerName("Steve")
                .setServerId("s-1")
                .build()
    }

    private fun run(line: String) = runtime.commands.execute(admin, line.split(" "))

    private fun said(
        message: Message,
        vararg values: Pair<String, Any?>,
    ) = messages.text(Message.PREFIX) + RichText.plain(" ") + messages.text(message, *values)

    @Test
    fun `servers are listed and found by display name`() {
        run("server list")
        run("server info LOBBY-789ABC")

        admin.replies shouldContain
            said(
                Message.SERVER_LIST_ENTRY,
                "server" to "lobby-789abc",
                "state" to "RUNNING",
                "players" to 3,
                "max" to 50,
            )
        admin.replies shouldContain said(Message.SERVER_INFO_ID, "id" to "s-1")
    }

    @Test
    fun `server commands carry the acting player and report refusals in words`() {
        run("server stop lobby-789abc")
        session.result = ServerCommandResult.newBuilder().setError(NodeErrors.NOT_PERMITTED).build()
        run("server kill lobby-789abc")

        session.commands.map { it.serverId to it.actingPlayerUuid } shouldBe
            listOf("s-1" to admin.player.toString(), "s-1" to admin.player.toString())
        admin.replies shouldContain said(Message.SERVER_STOPPING, "server" to "lobby-789abc")
        admin.replies shouldContain said(Message.FAILED, "error" to messages.text(Message.ERROR_NOT_PERMITTED))
    }

    @Test
    fun `maintenance changes go to the gateway as admin requests`() {
        session.adminResult = AdminRequestResult.newBuilder().setError(NodeErrors.UNKNOWN_GROUP).build()
        run("group maintenance nowhere true")

        session.adminRequests
            .single()
            .groupMaintenance.groupId shouldBe "nowhere"
        admin.replies shouldContain said(Message.FAILED, "error" to messages.text(Message.ERROR_UNKNOWN_GROUP))
    }

    @Test
    fun `ranks are set with v1 durations, checked before they leave`() {
        run("perm player set Steve vip 1x")
        run("perm player set Steve vip 1d 12h")
        run("perm player set Steve vip -1")

        admin.replies shouldContain said(Message.PERM_DURATION_INVALID, "duration" to "1x")
        val (timed, permanent) = session.adminRequests.map { it.rank }
        timed.playerUuid shouldBe steve.toString()
        timed.duration.seconds shouldBe Duration.ofHours(36).seconds
        permanent.hasDuration() shouldBe false
    }

    @Test
    fun `a rank shows how long it has left`() {
        queries.profiles[steve] =
            PlayerProfile
                .newBuilder()
                .setPermissionGroupId("vip")
                .setRankExpiresAt(now.plus(Duration.ofDays(2)).toTimestamp())
                .build()

        run("perm player get steve")

        admin.replies shouldContain said(Message.PERM_PLAYER_EXPIRES, "remaining" to "2d")
    }

    @Test
    fun `transfers go to a server or group, anything else is refused`() {
        run("player transfer Steve bedwars")
        run("player transfer Steve nowhere")

        session.sent
            .single { it.hasTransfer() }
            .transfer.groupId shouldBe "bedwars"
        admin.replies shouldContain said(Message.TARGET_NOT_FOUND, "target" to "nowhere")
    }

    @Test
    fun `console commands name their target and the acting player`() {
        run("command group bedwars say hello")

        val request = session.sent.single { it.hasNodeCommand() }.nodeCommand
        request.actingPlayerUuid shouldBe admin.player.toString()
        request.command.groupId shouldBe "bedwars"
        request.command.command shouldBe "say hello"
    }
}
