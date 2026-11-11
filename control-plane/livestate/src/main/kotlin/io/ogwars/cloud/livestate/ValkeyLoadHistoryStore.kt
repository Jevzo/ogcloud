package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.overview.LoadSample
import io.ogwars.cloud.domain.store.LoadHistoryStore
import io.lettuce.core.Range
import java.time.Duration
import java.time.Instant

/** Load samples in one sorted set scored by time; each write trims it to the window and refreshes its expiry. */
class ValkeyLoadHistoryStore(
    private val valkey: ValkeyConnection,
) : LoadHistoryStore {
    override fun record(
        sample: LoadSample,
        kept: Duration,
    ) {
        valkey.evalLong(
            LuaScripts.RECORD_IN_WINDOW,
            listOf(ValkeyKeys.LOAD_HISTORY),
            sample.at
                .toEpochMilli()
                .toString()
                .utf8(),
            RecordCodec.encode(sample),
            kept.millisArg(),
        )
    }

    override fun since(from: Instant): List<LoadSample> =
        valkey
            .sync()
            .zrangebyscore(ValkeyKeys.LOAD_HISTORY, Range.create(from.toEpochMilli(), Long.MAX_VALUE))
            .map { RecordCodec.decodeLoadSample(ValkeyKeys.LOAD_HISTORY, it) }
}
