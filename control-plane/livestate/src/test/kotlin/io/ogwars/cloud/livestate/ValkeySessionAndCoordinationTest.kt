package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.ReadModelKind
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ValkeySessionAndCoordinationTest : ValkeyTest() {
    private val sessions by lazy { ValkeySessionStore(valkey) }
    private val counter = AtomicLong()
    private val locks by lazy { ValkeyLockManager(valkey, IdGenerator { UUID(0, counter.incrementAndGet()) }) }
    private val alice = UUID(1, 1)
    private val bob = UUID(1, 2)

    private fun session(
        uuid: UUID,
        name: String,
    ) = PlayerSession(
        uuid,
        name,
        proxyId = UUID(2, 1),
        serverId = null,
        permissionGroupId = "default",
        rankVersion = 0,
        connectedAt = NOW,
    )

    @Test
    fun `sessions are found by uuid and by name, case-insensitively`() {
        sessions.put(session(alice, "Alice"))

        sessions.get(alice)?.value?.playerName shouldBe "Alice"
        sessions.findByName("ALICE")?.value?.playerUuid shouldBe alice
        sessions.onlinePlayers() shouldContainExactly setOf(alice)
    }

    @Test
    fun `session updates use compare-and-set`() {
        sessions.put(session(alice, "Alice"))
        val current = sessions.get(alice).shouldNotBeNull()

        sessions.compareAndSet(current.revision, current.value.copy(serverId = UUID(3, 1))) shouldBe true
        sessions.compareAndSet(current.revision, current.value.copy(serverId = UUID(3, 2))) shouldBe false
        sessions.get(alice)?.value?.serverId shouldBe UUID(3, 1)
    }

    @Test
    fun `removing a session keeps a name now used by someone else`() {
        sessions.put(session(alice, "Steve"))
        sessions.put(session(bob, "Steve"))

        sessions.remove(session(alice, "Steve"))

        sessions.findByName("steve")?.value?.playerUuid shouldBe bob
        sessions.onlinePlayers() shouldContainExactly setOf(bob)
    }

    @Test
    fun `a lock has one holder and only that holder releases it`() {
        val first = locks.tryAcquire("network-restart", Duration.ofSeconds(30)).shouldNotBeNull()
        locks.tryAcquire("network-restart", Duration.ofSeconds(30)) shouldBe null

        // The first lease runs out and someone else takes over; the old holder must not release it.
        valkey.sync().del(ValkeyKeys.lock("network-restart"))
        val second = locks.tryAcquire("network-restart", Duration.ofSeconds(30)).shouldNotBeNull()
        first.close()
        first.extend(Duration.ofSeconds(30)) shouldBe false

        locks.tryAcquire("network-restart", Duration.ofSeconds(30)) shouldBe null
        second.close()
        locks.tryAcquire("network-restart", Duration.ofSeconds(30)).shouldNotBeNull()
    }

    @Test
    fun `cooldowns, crash failures and read models`() {
        val cooldowns = ValkeyScalingCooldownStore(valkey)
        val failures = ValkeyCrashFailureStore(valkey)
        val readModels = ValkeyReadModelStore(valkey)
        val lobby = GroupId("lobby")

        cooldowns.record(lobby, NOW, Duration.ofMinutes(1))
        cooldowns.lastScaledAt(lobby) shouldBe NOW

        failures.record(lobby, NOW.minus(Duration.ofMinutes(20)), Duration.ofMinutes(10))
        failures.record(lobby, NOW, Duration.ofMinutes(10))
        failures.failures(lobby) shouldContainExactly listOf(NOW)
        failures.clear(lobby)
        failures.failures(lobby) shouldBe emptyList()

        readModels.put(ReadModelKind.GROUP, "lobby", byteArrayOf(1, 2, 3))
        readModels.get(ReadModelKind.GROUP, "lobby")?.toList() shouldBe listOf<Byte>(1, 2, 3)
        readModels.delete(ReadModelKind.GROUP, "lobby")
        readModels.get(ReadModelKind.GROUP, "lobby") shouldBe null
    }
}
