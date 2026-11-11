package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.overview.LoadSample
import io.ogwars.cloud.domain.store.LoadHistoryStore
import io.lettuce.core.Range
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/** Load samples in one sorted set scored by time; each write trims it to the window and refreshes its expiry. */
class ValkeyLoadHistoryStore(
    private val valkey: ValkeyConnection,
) : LoadHistoryStore {
    private val log = LoggerFactory.getLogger(javaClass)

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

    // A sample this version cannot read is left out, so one bad entry does not hide the whole day for 24 hours.
    override fun since(from: Instant): List<LoadSample> =
        valkey
            .sync()
            .zrangebyscore(ValkeyKeys.LOAD_HISTORY, Range.create(from.toEpochMilli(), Long.MAX_VALUE))
            .mapNotNull { bytes ->
                try {
                    RecordCodec.decodeLoadSample(ValkeyKeys.LOAD_HISTORY, bytes)
                } catch (unreadable: IllegalStateException) {
                    log.warn("Skipping a load sample: {}", unreadable.cause?.message ?: unreadable.message)
                    null
                }
            }
}
