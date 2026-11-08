package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.NodeErrors
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.NodeCommand
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.contracts.events.v1.PlayerPermissionsUpdated
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.node.core.query.FakeQueries
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.sdk.GameState
import io.ogwars.cloud.sdk.GroupType
import io.ogwars.cloud.sdk.LiveMessage
import io.ogwars.cloud.sdk.OgCloudException
import io.ogwars.cloud.sdk.RoutingStrategy
import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletionException

class ServerRuntimeTest {
    private val messages = Messages.load()
    private val platform = FakeServerPlatform()
    private val queries = FakeQueries()
    private val scheduler = ManualScheduler()
    private lateinit var session: FakeSession
    private val runtime =
        ServerRuntime(
            NodeIdentity("s-1", "lobby", GroupType.DYNAMIC, 50),
            platform,
            queries,
            messages,
            scheduler.executor,
            Runnable::run,
            mockk(),
        ) { listener -> FakeSession(listener).also { session = it } }
    private val api = runtime.api
    private val steve = UUID.randomUUID()

    init {
        runtime.start()
        session.listener.welcomed(Welcome.getDefaultInstance())
    }

    @Test
    fun `heartbeats report the platform's numbers`() {
        scheduler.runAll()

        val heartbeat = session.sent.last { it.hasHeartbeat() }.heartbeat
        heartbeat.players shouldBe 3
        heartbeat.maxPlayers shouldBe 50
        heartbeat.tps shouldBe 19.9
    }

    @Test
    fun `server requests complete with the new id or fail in the catalog's words`() {
        session.result = ServerCommandResult.newBuilder().setServerId("s-2").build()
        api.requestServer("bedwars").join() shouldBe "s-2"
        session.commands
            .last()
            .request.groupId shouldBe "bedwars"

        session.result = ServerCommandResult.newBuilder().setError(NodeErrors.NOT_PERMITTED).build()
        shouldThrow<CompletionException> { api.requestServer("bedwars").join() }
            .cause
            .shouldBeInstanceOf<OgCloudException>()
            .message shouldBe messages.text(Message.ERROR_NOT_PERMITTED)
    }

    @Test
    fun `the game state reaches the gateway now and after every welcome`() {
        api.gameState = GameState.INGAME
        session.listener.welcomed(Welcome.getDefaultInstance())

        session.sent
            .filter { it.hasGameState() }
            .map { it.gameState.gameState.name }
            .takeLast(2) shouldContainExactly
            listOf("GAME_STATE_INGAME", "GAME_STATE_INGAME")
    }

    @Test
    fun `template pushes and stops target this server`() {
        api.pushTemplate().join()
        api.stop().join()

        session.commands.map { it.commandCase.name } shouldContainExactly listOf("PUSH_TEMPLATE", "STOP")
        session.commands.map { it.serverId } shouldContainExactly listOf("", "")
    }

    @Test
    fun `transfers go out on the session and fail while disconnected`() {
        api.transferPlayerToGroup(steve, "bedwars", RoutingStrategy.MOST_FILLED).join()
        session.sent
            .last()
            .transfer.routingStrategy.name shouldBe "ROUTING_STRATEGY_MOST_FILLED"

        session.connected = false
        shouldThrow<CompletionException> { api.transferPlayer(steve, "s-2").join() }
            .cause
            .shouldBeInstanceOf<OgCloudException>()
    }

    @Test
    fun `console commands run on the platform and report whether it knew them`() {
        session.listener
            .deliver(command("say hi"))
            .join()
            .success shouldBe true
        session.listener
            .deliver(command("unknown"))
            .join()
            .success shouldBe false
        platform.consoleCommands shouldContainExactly listOf("say hi", "unknown")
    }

    @Test
    fun `a joined player's rank loads, and a rank change refreshes it`() {
        queries.permissionGroups["default"] = PermissionGroup.newBuilder().setPermissionGroupId("default").build()
        queries.permissionGroups["vip"] = PermissionGroup.newBuilder().setPermissionGroupId("vip").build()
        queries.profiles[steve] = profile("default", 1)
        runtime.joined(steve).join()

        queries.profiles[steve] = profile("vip", 2)
        session.listener.broadcast(
            NodeBroadcast
                .newBuilder()
                .setPlayerPermissions(
                    PlayerPermissionsUpdated.newBuilder().setPlayerUuid(steve.toString()).setVersion(2),
                ).build(),
        )

        runtime.permissions
            .rank(steve)
            ?.group
            ?.permissionGroupId shouldBe "vip"
        platform.rankChanges shouldContainExactly listOf(setOf(steve), setOf(steve))
    }

    @Test
    fun `every Welcome lets the platform reload what the gateway does not replay, such as NPCs`() {
        session.listener.welcomed(Welcome.getDefaultInstance())

        platform.welcomes shouldBe 2
    }

    @Test
    fun `NPC updates reach the platform`() {
        session.listener.broadcast(NodeBroadcast.newBuilder().setNpc(NpcUpdated.newBuilder().setNpcId("n-1")).build())

        platform.npcUpdates.map { it.npcId } shouldContainExactly listOf("n-1")
    }

    @Test
    fun `live channels subscribe once per channel and deliver to every listener`() {
        val received = mutableListOf<String>()
        val first = api.liveChannels.subscribe("party") { received += "first:" + String(it.payload()) }
        val second = api.liveChannels.subscribe("party") { _: LiveMessage -> error("a broken listener") }
        api.liveChannels.subscribe("party") { received += "third:" + it.typeId }
        session.listener.liveMessage(
            LiveChannelDelivery
                .newBuilder()
                .setChannel("party")
                .setTypeId("invite")
                .setPayload(ByteString.copyFromUtf8("hi"))
                .build(),
        )
        first.close()
        second.close()

        received shouldContainExactly listOf("first:hi", "third:invite")
        session.subscriptions shouldContainExactly listOf("+party")
    }

    @Test
    fun `the last listener to leave unsubscribes, and a new one subscribes again`() {
        val first = api.liveChannels.subscribe("party") { }
        val second = api.liveChannels.subscribe("party") { }

        first.close()
        second.close()
        api.liveChannels.subscribe("party") { }

        session.subscriptions shouldContainExactly listOf("+party", "-party", "+party")
    }

    @Test
    fun `every listener gets its own copy of the payload`() {
        val seen = mutableListOf<String>()
        api.liveChannels.subscribe("party") { it.payload().fill(0) }
        api.liveChannels.subscribe("party") { seen += String(it.payload()) }

        session.listener.liveMessage(
            LiveChannelDelivery
                .newBuilder()
                .setChannel("party")
                .setPayload(ByteString.copyFromUtf8("hi"))
                .build(),
        )

        seen shouldContainExactly listOf("hi")
    }

    @Test
    fun `live channel names and payloads are checked before they leave`() {
        shouldThrow<IllegalArgumentException> { api.liveChannels.publish("Party!", "t", ByteArray(1)) }
        shouldThrow<IllegalArgumentException> { api.liveChannels.publish("party", "t", ByteArray(65_537)) }

        api.liveChannels.publish("party", "t", ByteArray(1))
        session.sent.last().messageCase shouldBe SessionRequest.MessageCase.LIVE_PUBLISH
    }

    private fun command(text: String) =
        Delivery.newBuilder().setCommand(NodeCommand.newBuilder().setAll(true).setCommand(text)).build()

    private fun profile(
        group: String,
        version: Long,
    ) = PlayerProfile
        .newBuilder()
        .setPlayerUuid(steve.toString())
        .setPermissionGroupId(group)
        .setVersion(version)
        .build()
}
