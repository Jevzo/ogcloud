package io.ogwars.cloud.domain.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ServerTest {
    @ParameterizedTest
    @CsvSource(
        "REQUESTED, PREPARING",
        "REQUESTED, STOPPING",
        "PREPARING, STARTING",
        "STARTING, RUNNING",
        "RUNNING, DRAINING",
        "DRAINING, RUNNING",
        "DRAINING, STOPPING",
        "STOPPING, STOPPED",
    )
    fun `allows the lifecycle transitions`(
        from: ServerState,
        to: ServerState,
    ) {
        val moved = server(state = from).transitionTo(to, T0.plusSeconds(5))

        moved.state shouldBe to
        moved.stateSince shouldBe T0.plusSeconds(5)
    }

    @ParameterizedTest
    @CsvSource(
        "REQUESTED, RUNNING",
        "RUNNING, STARTING",
        "STOPPED, RUNNING",
        "STOPPING, RUNNING",
        "PREPARING, DRAINING",
    )
    fun `rejects transitions outside the table`(
        from: ServerState,
        to: ServerState,
    ) {
        shouldThrow<IllegalStateException> { server(state = from).transitionTo(to, T0) }
    }

    @Test
    fun `entering STARTING records the start time`() {
        server(state = ServerState.PREPARING).transitionTo(ServerState.STARTING, T0).startedAt shouldBe T0
    }

    @Test
    fun `names keep the full id so group names may contain dashes`() {
        val server = server(group = "staff-lobby")

        server.podName shouldBe "staff-lobby-0192d2c11a2b7c3d8e4f123456789abc"
        server.displayName shouldBe "staff-lobby-0192d2"
    }
}
