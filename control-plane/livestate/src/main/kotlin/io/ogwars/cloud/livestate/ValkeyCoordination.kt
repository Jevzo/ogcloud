package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.Lock
import io.ogwars.cloud.domain.store.LockManager
import io.ogwars.cloud.domain.store.OneTimeKind
import io.ogwars.cloud.domain.store.OneTimeStore
import io.ogwars.cloud.domain.store.RateDecision
import io.ogwars.cloud.domain.store.RateLimit
import io.ogwars.cloud.domain.store.RateLimiter
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.RotatedTokenStore
import io.ogwars.cloud.domain.store.ScalingCooldownStore
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
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

    // SCAN walks the keyspace in small steps instead of blocking Valkey like KEYS.
    override fun ids(kind: ReadModelKind): Set<String> {
        val prefix = ValkeyKeys.readModel(kind, "")
        val ids = mutableSetOf<String>()
        var cursor = ScanCursor.INITIAL
        do {
            val page = valkey.sync().scan(cursor, ScanArgs.Builder.matches("$prefix*").limit(SCAN_PAGE))
            page.keys.mapTo(ids) { it.removePrefix(prefix) }
            cursor = page
        } while (!page.isFinished)
        return ids
    }

    private companion object {
        const val SCAN_PAGE = 500L
    }
}

class ValkeyRotatedTokenStore(
    private val valkey: ValkeyConnection,
) : RotatedTokenStore {
    override fun remember(
        tokenHash: String,
        authorizationId: String,
        ttl: Duration,
    ) {
        valkey.sync().set(
            ValkeyKeys.rotatedRefreshToken(tokenHash),
            authorizationId.utf8(),
            SetArgs().px(ttl.toMillis()),
        )
    }

    override fun authorizationOf(tokenHash: String): String? =
        valkey.sync().get(ValkeyKeys.rotatedRefreshToken(tokenHash))?.utf8()
}

class ValkeyRateLimiter(
    private val valkey: ValkeyConnection,
) : RateLimiter {
    override fun tryAcquire(
        bucket: String,
        client: String,
        limit: RateLimit,
        at: Instant,
    ): RateDecision {
        val waitMillis =
            valkey.evalLong(
                LuaScripts.TOKEN_BUCKET,
                listOf(ValkeyKeys.rateLimit(bucket, client)),
                limit.capacity.toString().utf8(),
                limit.perMinute.toString().utf8(),
                at.toEpochMilli().toString().utf8(),
            )
        return RateDecision(waitMillis == 0L, Duration.ofMillis(waitMillis))
    }
}

class ValkeyOneTimeStore(
    private val valkey: ValkeyConnection,
) : OneTimeStore {
    override fun put(
        kind: OneTimeKind,
        id: String,
        value: String,
        ttl: Duration,
    ) {
        valkey.sync().set(ValkeyKeys.oneTime(kind, id), value.utf8(), SetArgs().px(ttl.toMillis()))
    }

    override fun take(
        kind: OneTimeKind,
        id: String,
    ): String? = valkey.sync().getdel(ValkeyKeys.oneTime(kind, id))?.utf8()
}
