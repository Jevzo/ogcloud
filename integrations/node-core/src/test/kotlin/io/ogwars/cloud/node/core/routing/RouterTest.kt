package io.ogwars.cloud.node.core.routing

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.ogwars.cloud.node.core.state.Backend
import io.ogwars.cloud.node.core.state.NetworkState
import io.ogwars.cloud.node.core.state.ServerDirectory
import io.ogwars.cloud.node.core.text.Message
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RouterTest {
    private val network = NetworkState()
    private val directory =
        ServerDirectory(
            object : ServerDirectory.Listener {
                override fun registered(backend: Backend) = Unit

                override fun unregistered(backend: Backend) = Unit
            },
        )
    private val router = Router(directory, network)
    private val players = mutableMapOf("a" to 5, "b" to 20, "c" to 10, "full" to 50)

    init {
        network.update(
            NetworkSettingsUpdated
                .newBuilder()
                .setVersion(1)
                .setDefaultGroupId("lobby")
                .setRoutingStrategy(RoutingStrategy.ROUTING_STRATEGY_BALANCED)
                .build(),
        )
        directory.reset(
            listOf(
                server("a", "lobby", max = 10),
                server("b", "lobby", max = 100),
                server("c", "lobby", max = 50),
                server("full", "lobby", max = 50),
                server("d", "lobby", max = 50, state = ServerState.SERVER_STATE_DRAINING),
                server("s", "smp", max = 20),
                server("x", "bedwars", max = 16),
                server("u1", "unlimited", max = 0),
                server("u2", "unlimited", max = 0),
            ),
        )
    }

    private fun server(
        id: String,
        group: String,
        max: Int,
        state: ServerState = ServerState.SERVER_STATE_RUNNING,
    ) = ServerInfo
        .newBuilder()
        .setServerId(id)
        .setDisplayName("$group-$id")
        .setGroupId(group)
        .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
        .setState(state)
        .setHost("10.0.0.1")
        .setMaxPlayers(max)
        .build()

    private fun pick(strategy: RoutingStrategy?) =
        (
            router.route(
                Destination.Group("lobby", strategy),
                bypass = false,
            ) { players[it.id] ?: 0 } as Route.To
        ).backend.id

    @Test
    fun `strategies pick among open backends, full and draining ones take nobody`() {
        pick(null) shouldBe "b"
        pick(RoutingStrategy.ROUTING_STRATEGY_LEAST_FILLED) shouldBe "a"
        pick(RoutingStrategy.ROUTING_STRATEGY_MOST_FILLED) shouldBe "b"
        List(4) { pick(RoutingStrategy.ROUTING_STRATEGY_ROUND_ROBIN) } shouldContainExactly listOf("a", "b", "c", "a")
    }

    // A backend checks its own limit only at the end of a switch, when the player has left their old server (D-0142).
    @Test
    fun `a player a backend turned away goes to another backend of its group`() {
        val lobby = checkNotNull(directory["b"])
        val smp = checkNotNull(directory["s"])

        (router.instead(lobby, bypass = false) { players[it.id] ?: 0 } as Route.To).backend.id shouldBe "c"
        router.instead(smp, bypass = false) { 0 } shouldBe Route.NoRoom
    }

    // Its count may still show room, and a backend without a limit would otherwise look emptiest.
    @Test
    fun `a backend that just turned players away as full takes nobody from a group route`() {
        val route = router.route(Destination.Group("unlimited"), bypass = false, full = setOf("u1")) { 0 }

        (route as Route.To).backend.id shouldBe "u2"
        router.route(Destination.Group("unlimited"), bypass = false, full = setOf("u1", "u2")) { 0 } shouldBe
            Route.NoRoom
    }

    @Test
    fun `a group in maintenance sends players without the bypass to the default group`() {
        network.update(maintenance("bedwars", on = true))

        (router.route(Destination.Group("bedwars"), bypass = true) { 0 } as Route.To).backend.id shouldBe "x"
        (router.route(Destination.Group("bedwars"), bypass = false) { 0 } as Route.To).backend.groupId shouldBe "lobby"
    }

    @Test
    fun `players are kicked when even the default group is closed or empty`() {
        network.update(maintenance("lobby", on = true))
        router.initial(bypass = false) { 0 } shouldBe Route.Kick(Message.KICK_GROUP_MAINTENANCE)

        router.route(Destination.Group("nowhere"), bypass = false) { 0 } shouldBe Route.NoRoom
    }

    private fun maintenance(
        group: String,
        on: Boolean,
    ) = GroupUpdated
        .newBuilder()
        .setVersion(2)
        .setGroupId(group)
        .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
        .setMaintenance(on)
        .build()
}
