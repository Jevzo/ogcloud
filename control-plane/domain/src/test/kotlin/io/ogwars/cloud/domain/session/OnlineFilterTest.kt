package io.ogwars.cloud.domain.session

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OnlineFilterTest {
    private val proxy = UUID(2, 1)
    private val backend = UUID(3, 1)
    private val alice = PlayerSession(UUID(1, 1), "Alice", proxy, backend, "default", 0, Instant.EPOCH)
    private val joining = alice.copy(playerUuid = UUID(1, 2), playerName = "Bob", serverId = null)

    @Test
    fun `no filter keeps everyone`() {
        OnlineFilter.ALL.keeps(alice) shouldBe true
        OnlineFilter.ALL.keeps(joining) shouldBe true
    }

    @Test
    fun `a name prefix matches ignoring case`() {
        OnlineFilter(namePrefix = "al").keeps(alice) shouldBe true
        OnlineFilter(namePrefix = "AL").keeps(joining) shouldBe false
    }

    @Test
    fun `a server keeps the players on it, and a proxy those connected through it`() {
        OnlineFilter(serverId = backend).keeps(alice) shouldBe true
        OnlineFilter(serverId = backend).keeps(joining) shouldBe false
        OnlineFilter(serverId = proxy).keeps(joining) shouldBe true
        OnlineFilter(serverId = UUID(9, 9)).keeps(alice) shouldBe false
    }

    @Test
    fun `both narrow together`() {
        OnlineFilter(namePrefix = "a", serverId = backend).keeps(alice) shouldBe true
        OnlineFilter(namePrefix = "b", serverId = backend).keeps(alice) shouldBe false
    }
}
