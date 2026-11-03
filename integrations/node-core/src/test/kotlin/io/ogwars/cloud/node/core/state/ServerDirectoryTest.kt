package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ServerDirectoryTest {
    private val events = mutableListOf<String>()
    private val directory =
        ServerDirectory(
            object : ServerDirectory.Listener {
                override fun registered(backend: Backend) {
                    events += "+${backend.id}"
                }

                override fun unregistered(backend: Backend) {
                    events += "-${backend.id}"
                }
            },
        )

    private fun info(
        id: String,
        state: ServerState = ServerState.SERVER_STATE_RUNNING,
        type: GroupType = GroupType.GROUP_TYPE_DYNAMIC,
        host: String = "10.0.0.1",
    ) = ServerInfo
        .newBuilder()
        .setServerId(id)
        .setDisplayName("lobby-$id")
        .setGroupId("lobby")
        .setGroupType(type)
        .setState(state)
        .setHost(host)
        .setPort(25565)
        .setMaxPlayers(50)
        .build()

    private fun event(
        id: String,
        state: ServerState,
        host: String = "10.0.0.1",
    ) = ServerLifecycleChanged
        .newBuilder()
        .setServerId(id)
        .setDisplayName("lobby-$id")
        .setGroupId("lobby")
        .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
        .setState(state)
        .setHost(host)
        .setPort(25565)
        .build()

    @Test
    fun `the welcome registers running backends with an address, never proxies or pending servers`() {
        directory.reset(
            listOf(
                info("a"),
                info("b", state = ServerState.SERVER_STATE_STARTING),
                info("c", host = ""),
                info("p", type = GroupType.GROUP_TYPE_PROXY),
                info("d", state = ServerState.SERVER_STATE_DRAINING),
            ),
        )

        events shouldContainExactly listOf("+a", "+d")
        directory["d"]?.draining shouldBe true
        directory.find("LOBBY-A")?.id shouldBe "a"
    }

    @Test
    fun `lifecycle events add, drain and remove backends, a new welcome drops what it no longer lists`() {
        directory.reset(listOf(info("a")))
        directory.apply(event("b", ServerState.SERVER_STATE_RUNNING))
        directory.apply(event("b", ServerState.SERVER_STATE_DRAINING))
        directory.apply(event("a", ServerState.SERVER_STATE_STOPPING))
        directory.apply(event("b", ServerState.SERVER_STATE_RUNNING, host = "10.0.0.2"))
        directory.reset(listOf(info("c")))

        events shouldContainExactly listOf("+a", "+b", "-a", "+b", "-b", "+c")
        directory.all().map { it.id } shouldContainExactly listOf("c")
    }
}
