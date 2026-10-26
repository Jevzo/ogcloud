package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.Lock
import io.ogwars.cloud.domain.store.LockManager
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.ScalingCooldownStore
import io.lettuce.core.SetArgs
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ValkeyLockManager(
    private val valkey: ValkeyConnection,
    private val tokens: IdGenerator,
) : LockManager {
    override fun tryAcquire(
        name: String,
        lease: Duration,
    ): Lock? {
        val token = tokens.next().toString()
        val acquired = valkey.sync().set(ValkeyKeys.lock(name), token.utf8(), SetArgs().nx().px(lease.toMillis()))
        return if (acquired == OK) ValkeyLock(name, token) else null
    }

    private inner class ValkeyLock(
        override val name: String,
        private val token: String,
    ) : Lock {
        override fun extend(lease: Duration): Boolean =
            valkey.evalLong(LuaScripts.EXTEND_LOCK, listOf(ValkeyKeys.lock(name)), token.utf8(), lease.millisArg()) ==
                1L

        override fun close() {
            valkey.evalLong(LuaScripts.RELEASE_LOCK, listOf(ValkeyKeys.lock(name)), token.utf8())
        }
    }

    private companion object {
        const val OK = "OK"
    }
}

class ValkeyScalingCooldownStore(
    private val valkey: ValkeyConnection,
) : ScalingCooldownStore {
    override fun lastScaledAt(groupId: GroupId): Instant? =
        valkey.sync().get(ValkeyKeys.scalingCooldown(groupId))?.let { Instant.ofEpochMilli(it.utf8().toLong()) }

    override fun record(
        groupId: GroupId,
        at: Instant,
        cooldown: Duration,
    ) {
        // A zero cooldown still needs a positive expiry; the value is irrelevant once it passes.
        val ttl = maxOf(cooldown, Duration.ofMillis(1))
        valkey.sync().set(
            ValkeyKeys.scalingCooldown(groupId),
            at.toEpochMilli().toString().utf8(),
            SetArgs().px(ttl.toMillis()),
        )
    }
}

class ValkeyCrashFailureStore(
    private val valkey: ValkeyConnection,
) : CrashFailureStore {
    override fun record(
        groupId: GroupId,
        serverId: UUID,
        at: Instant,
        window: Duration,
    ) {
        val millis = at.toEpochMilli()
        // The server id keeps simultaneous failures of different pods apart.
        valkey.evalLong(
            LuaScripts.RECORD_FAILURE,
            listOf(ValkeyKeys.crashLoop(groupId)),
            millis.toString().utf8(),
            "$millis:$serverId".utf8(),
            window.millisArg(),
        )
    }

    override fun failures(groupId: GroupId): List<Instant> =
        valkey
            .sync()
            .zrangeWithScores(ValkeyKeys.crashLoop(groupId), 0, -1)
            .map { Instant.ofEpochMilli(it.score.toLong()) }

    override fun pause(groupId: GroupId) {
        valkey.sync().set(ValkeyKeys.crashLoopPaused(groupId), PAUSED)
    }

    override fun isPaused(groupId: GroupId): Boolean = valkey.sync().exists(ValkeyKeys.crashLoopPaused(groupId)) == 1L

    override fun clear(groupId: GroupId) {
        valkey.sync().del(ValkeyKeys.crashLoop(groupId), ValkeyKeys.crashLoopPaused(groupId))
    }

    private companion object {
        val PAUSED = "1".utf8()
    }
}

class ValkeyReadModelStore(
    private val valkey: ValkeyConnection,
) : ReadModelStore {
    override fun put(
        kind: ReadModelKind,
        id: String,
        encoded: ByteArray,
    ) {
        valkey.sync().set(ValkeyKeys.readModel(kind, id), encoded)
    }

    override fun get(
        kind: ReadModelKind,
        id: String,
    ): ByteArray? = valkey.sync().get(ValkeyKeys.readModel(kind, id))

    override fun delete(
        kind: ReadModelKind,
        id: String,
    ) {
        valkey.sync().del(ValkeyKeys.readModel(kind, id))
    }
}
