package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.LinkCodeIssued
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ResolveLoginResponse
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.node.core.query.FakeQueries
import io.ogwars.cloud.node.core.routing.Route
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import io.ogwars.cloud.sdk.GroupType as SdkGroupType

class ProxyRuntimeTest {
    private val messages = Messages.load()
    private val platform = FakeProxyPlatform()
    private val queries = FakeQueries()
    private val scheduler = ManualScheduler()
    private lateinit var session: FakeSession
    private val runtime =
        ProxyRuntime(
            NodeIdentity("p-1", "proxy", SdkGroupType.PROXY, MAX_PLAYERS),
            platform,
            queries,
            messages,
            scheduler.executor,
            Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC),
        ) { listener -> FakeSession(listener).also { session = it } }
    private val steve = UUID.randomUUID()
    private val admin = UUID.randomUUID()

    init {
        queries.permissionGroups["default"] = PermissionGroup.newBuilder().setPermissionGroupId("default").build()
        runtime.start()
        session.listener.welcomed(
            Welcome
                .newBuilder()
                .setNetworkSettings(NetworkSettingsUpdated.newBuilder().setVersion(1).setDefaultGroupId("lobby"))
                .addServers(server("l-1", "lobby"))
                .addServers(server("b-1", "bedwars"))
                .build(),
        )
        platform.players += OnlinePlayer(steve, "Steve", "b-1")
        platform.players += OnlinePlayer(admin, "Admin", "l-1")
        platform.granted[admin] = mutableSetOf(PermissionNodes.NOTIFY, PermissionNodes.MAINTENANCE_BYPASS)
    }

    private fun server(
        id: String,
        group: String,
    ) = ServerInfo
        .newBuilder()
        .setServerId(id)
        .setDisplayName("$group-$id")
        .setGroupId(group)
        .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
        .setState(ServerState.SERVER_STATE_RUNNING)
        .setHost("10.0.0.1")
        .setMaxPlayers(50)
        .build()

    @Test
    fun `the welcome registers backends, opens the SDK and starts the heartbeat and snapshots`() {
        var ready = false
        runtime.api.onReady { ready = true }
        scheduler.runAll()

        session.started shouldBe true
        ready shouldBe true
        platform.registered shouldContainExactlyInAnyOrder listOf("l-1", "b-1")
        session.sent.map { it.messageCase } shouldContainExactly
            listOf(SessionRequest.MessageCase.HEARTBEAT, SessionRequest.MessageCase.PLAYER_SNAPSHOT)
        session.sent[1]
            .playerSnapshot.playersList
            .map { it.playerName to it.serverId } shouldContainExactlyInAnyOrder
            listOf("Steve" to "b-1", "Admin" to "l-1")
    }

    @Test
    fun `logins ask the controller and load the rank, and a gateway failure turns players away politely`() {
        queries.login =
            ResolveLoginResponse
                .newBuilder()
                .setAllowed(true)
                .setProfile(profile(steve))
                .build()
        runtime.login(steve, "Steve", 47, "1.2.3.4").join() shouldBe LoginDecision.Allowed
        runtime.permissions
            .rank(steve)
            ?.group
            ?.permissionGroupId shouldBe "default"

        queries.login =
            ResolveLoginResponse
                .newBuilder()
                .setAllowed(false)
                .setKickMessage("&cMaintenance")
                .build()
        runtime.login(steve, "Steve", 47, "1.2.3.4").join() shouldBe LoginDecision.Denied("&cMaintenance")

        queries.login = null
        runtime.login(steve, "Steve", 47, "1.2.3.4").join() shouldBe
            LoginDecision.Denied(messages.text(Message.KICK_LOGIN_FAILED))
    }

    @Test
    fun `a login that never completes forgets its rank and reports nothing`() {
        queries.login =
            ResolveLoginResponse
                .newBuilder()
                .setAllowed(true)
                .setProfile(profile(steve))
                .build()
        runtime.login(steve, "Steve", 47, "1.2.3.4").join()

        runtime.loginAbandoned(steve)

        runtime.permissions.rank(steve) shouldBe null
        session.sent.none { it.hasPlayerActivity() } shouldBe true
    }

    @Test
    fun `a full proxy turns players away before asking anyone`() {
        repeat(MAX_PLAYERS) { platform.players += OnlinePlayer(UUID.randomUUID(), "p$it", null) }

        runtime.login(UUID.randomUUID(), "Late", 47, "1.2.3.4").join() shouldBe
            LoginDecision.Denied(messages.text(Message.KICK_PROXY_FULL))
    }

    @Test
    fun `players are reported as they join, switch and leave`() {
        runtime.joined(steve, "Steve", 47)
        runtime.switched(steve, null, "l-1")
        runtime.left(steve)

        session.sent.map { it.playerActivity.activityCase.name } shouldContainExactly
            listOf("CONNECTED", "SWITCHED", "DISCONNECTED")
        runtime
            .initialServer(steve)
            .shouldBeInstanceOf<Route.To>()
            .backend.id shouldBe "l-1"
    }

    @Test
    fun `transfers route each player, and a gone player fails the delivery`() {
        val toLobby = transfer { setPlayerUuid(steve.toString()).setGroupId("lobby") }
        val everyone = transfer { setFromServerId("l-1").setKickMessage("&cBye") }
        val ghost = transfer { setPlayerUuid(UUID.randomUUID().toString()).setGroupId("lobby") }

        session.listener
            .deliver(toLobby)
            .join()
            .success shouldBe true
        session.listener
            .deliver(everyone)
            .join()
            .success shouldBe true
        session.listener
            .deliver(ghost)
            .join()
            .success shouldBe false
        platform.connects shouldContainExactly listOf(steve to "l-1")
        platform.kicks shouldContainExactly listOf(admin to "&cBye")
    }

    @Test
    fun `link codes reach the player on this proxy`() {
        val code =
            Delivery
                .newBuilder()
                .setLinkCode(
                    LinkCodeIssued.newBuilder().setPlayerUuid(steve.toString()).setCode("123456"),
                ).build()

        session.listener
            .deliver(code)
            .join()
            .success shouldBe true
        platform.messages.single().second shouldBe messages.text(Message.LINK_CODE, "code" to "123456")
    }

    @Test
    fun `network maintenance kicks players without the bypass and tells admins`() {
        session.listener.broadcast(
            NodeBroadcast
                .newBuilder()
                .setNetworkSettings(
                    NetworkSettingsUpdated
                        .newBuilder()
                        .setVersion(2)
                        .setDefaultGroupId("lobby")
                        .setMaintenance(true)
                        .setMaintenanceKickMessage("&cMaintenance"),
                ).build(),
        )

        platform.kicks shouldContainExactly listOf(steve to "&cMaintenance")
        platform.messages.map { it.first } shouldContainExactly listOf(admin)
    }

    @Test
    fun `group maintenance moves players without the bypass to the default group`() {
        session.listener.broadcast(maintenance("bedwars"))

        platform.connects shouldContainExactly listOf(steve to "l-1")
        platform.messages.single().second shouldBe
            messages.text(
                Message.NOTIFY_GROUP_MAINTENANCE,
                "group" to "bedwars",
                "status" to messages.text(Message.STATUS_ENABLED),
            )
    }

    @Test
    fun `maintenance of this proxy's own group kicks players without the bypass`() {
        session.listener.broadcast(maintenance("proxy"))

        platform.kicks shouldContainExactly listOf(steve to messages.text(Message.KICK_GROUP_MAINTENANCE))
    }

    @Test
    fun `a new Welcome replaces group flags learned before it and asks the gateway for every group`() {
        session.listener.broadcast(maintenance("bedwars"))
        queries.groups +=
            GroupInfo
                .newBuilder()
                .setGroupId("arena")
                .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
                .setMaintenance(true)
                .build()

        // While the proxy was away, bedwars left maintenance and arena, without servers yet, entered it.
        session.listener.welcomed(
            Welcome
                .newBuilder()
                .setNetworkSettings(NetworkSettingsUpdated.newBuilder().setVersion(1).setDefaultGroupId("lobby"))
                .addServers(server("b-1", "bedwars"))
                .build(),
        )

        runtime.network.inMaintenance("bedwars") shouldBe false
        runtime.network.inMaintenance("arena") shouldBe true
    }

    @Test
    fun `ranks held before a reconnect are loaded again, as broadcasts may have been missed`() {
        queries.login =
            ResolveLoginResponse
                .newBuilder()
                .setAllowed(true)
                .setProfile(profile(steve))
                .build()
        runtime.login(steve, "Steve", 47, "1.2.3.4").join()
        queries.permissionGroups["vip"] = PermissionGroup.newBuilder().setPermissionGroupId("vip").build()
        queries.profiles[steve] =
            profile(steve)
                .toBuilder()
                .setPermissionGroupId("vip")
                .setVersion(2)
                .build()

        session.listener.welcomed(
            Welcome.newBuilder().setNetworkSettings(NetworkSettingsUpdated.newBuilder().setVersion(1)).build(),
        )

        runtime.permissions
            .rank(steve)
            ?.group
            ?.permissionGroupId shouldBe "vip"
        platform.rankChanges.flatten() shouldContain steve
    }

    @Test
    fun `lifecycle events update the directory and tell admins`() {
        session.listener.lifecycle(
            ServerLifecycleChanged
                .newBuilder()
                .setServerId("b-1")
                .setDisplayName("bedwars-b-1")
                .setGroupId("bedwars")
                .setState(ServerState.SERVER_STATE_STOPPING)
                .build(),
        )

        platform.registered shouldContainExactly listOf("l-1")
        platform.messages.single().second shouldBe
            messages.text(Message.NOTIFY_SERVER, "server" to "bedwars-b-1", "state" to "STOPPING", "group" to "bedwars")
    }

    private fun maintenance(group: String) =
        NodeBroadcast
            .newBuilder()
            .setGroup(
                GroupUpdated
                    .newBuilder()
                    .setVersion(5)
                    .setGroupId(group)
                    .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
                    .setMaintenance(true),
            ).build()

    private fun transfer(request: TransferRequested.Builder.() -> Unit) =
        Delivery.newBuilder().setTransfer(TransferRequested.newBuilder().apply(request)).build()

    private fun profile(player: UUID) =
        PlayerProfile
            .newBuilder()
            .setPlayerUuid(player.toString())
            .setPermissionGroupId("default")
            .build()

    private companion object {
        const val MAX_PLAYERS = 10
    }
}
