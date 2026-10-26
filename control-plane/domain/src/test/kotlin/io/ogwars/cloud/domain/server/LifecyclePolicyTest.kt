package io.ogwars.cloud.domain.server

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration

class LifecyclePolicyTest {
    private val policy = LifecyclePolicy()
    private val lobby = GroupId("lobby")
    private val dynamicGroup = group()

    @ParameterizedTest
    @CsvSource("REQUESTED, 61", "PREPARING, 181", "STARTING, 91", "STOPPING, 181")
    fun `servers stuck before RUNNING or in STOPPING are cleaned up`(
        state: ServerState,
        seconds: Long,
    ) {
        val stuck = server(state = state)

        policy
            .overdueAction(
                stuck,
                dynamicGroup,
                lobby,
                T0.plusSeconds(seconds),
            ).shouldBeInstanceOf<OverdueAction.CleanUp>()
        policy.overdueAction(stuck, dynamicGroup, lobby, T0.plusSeconds(seconds - 2)) shouldBe OverdueAction.None
    }

    @Test
    fun `a running server without heartbeat for 30s is stopped`() {
        val running = server(lastHeartbeatAt = T0)

        policy.overdueAction(running, dynamicGroup, lobby, T0.plusSeconds(30)) shouldBe OverdueAction.None
        policy.overdueAction(running, dynamicGroup, lobby, T0.plusSeconds(31)).shouldBeInstanceOf<OverdueAction.Stop>()
    }

    @Test
    fun `an empty draining server stops right away`() {
        val draining = server(state = ServerState.DRAINING, players = 0)

        policy.overdueAction(draining, dynamicGroup, lobby, T0).shouldBeInstanceOf<OverdueAction.Stop>()
    }

    @Test
    fun `after the drain timeout players are transferred, then the server is forced down`() {
        val draining = server(state = ServerState.DRAINING, players = 3)
        val minecraft = group(id = "bedwars")

        policy.overdueAction(draining, minecraft, lobby, T0.plusSeconds(59)) shouldBe OverdueAction.None
        policy.overdueAction(draining, minecraft, lobby, T0.plusSeconds(60)) shouldBe
            OverdueAction.TransferPlayers(TransferTarget.ToGroup(lobby))
        policy.overdueAction(draining, minecraft, lobby, T0.plusSeconds(90)).shouldBeInstanceOf<OverdueAction.Stop>()
    }

    @Test
    fun `draining proxies with players are never forced down`() {
        val proxy = server(state = ServerState.DRAINING, groupType = GroupType.PROXY, players = 10)
        val proxies = group(type = GroupType.PROXY, id = "proxy")

        policy.overdueAction(proxy, proxies, lobby, T0.plus(Duration.ofHours(2))) shouldBe OverdueAction.None
    }

    @Test
    fun `drain stops at once when empty or when the group has no drain timeout`() {
        policy.drainAction(server(players = 0), dynamicGroup) shouldBe DrainAction.STOP_NOW
        policy.drainAction(server(players = 4), group(drainTimeout = Duration.ZERO)) shouldBe DrainAction.STOP_NOW
        policy.drainAction(server(players = 4), dynamicGroup) shouldBe DrainAction.START_DRAINING
        policy.drainAction(server(state = ServerState.STARTING, players = 4), dynamicGroup) shouldBe DrainAction.IGNORE
    }

    @Test
    fun `graceful stop drains running servers and stops pending ones`() {
        policy.gracefulStopAction(server()) shouldBe GracefulStopAction.DRAIN
        policy.gracefulStopAction(server(state = ServerState.PREPARING)) shouldBe GracefulStopAction.STOP_NOW
        policy.gracefulStopAction(server(state = ServerState.DRAINING)) shouldBe GracefulStopAction.IGNORE
    }

    @Test
    fun `game states apply only to running dynamic servers, and ENDING ends the game`() {
        policy.gameStateAction(server(), GameState.INGAME) shouldBe GameStateAction.UPDATE
        policy.gameStateAction(server(), GameState.ENDING) shouldBe GameStateAction.END_GAME
        policy.gameStateAction(server(groupType = GroupType.STATIC), GameState.INGAME) shouldBe GameStateAction.IGNORE
        policy.gameStateAction(server(state = ServerState.STARTING), GameState.INGAME) shouldBe GameStateAction.IGNORE
    }

    @Test
    fun `players of a proxy are kicked, everyone else goes to the default group`() {
        policy.transferTarget(server(groupType = GroupType.PROXY), lobby) shouldBe TransferTarget.Kick
        policy.transferTarget(server(groupType = GroupType.STATIC), lobby) shouldBe TransferTarget.ToGroup(lobby)
        policy.transferTarget(server(), lobby) shouldBe TransferTarget.ToGroup(lobby)
    }
}
