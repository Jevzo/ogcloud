package io.ogwars.cloud.livestate

import io.lettuce.core.ScriptOutputType
import java.time.Duration
import java.util.UUID

internal fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)

internal fun ByteArray.utf8(): String = toString(Charsets.UTF_8)

internal fun Duration.millisArg(): ByteArray = toMillis().toString().utf8()

/** An id read back from Valkey; a malformed one is corrupt state, not a caller's mistake. */
internal fun String.storedUuid(): UUID =
    try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("Corrupt id '$this' in Valkey", e)
    }

/** Runs a script that returns an integer. */
internal fun ValkeyConnection.evalLong(
    script: String,
    keys: List<String>,
    vararg args: ByteArray,
): Long = sync().eval<Long>(script.trimIndent(), ScriptOutputType.INTEGER, keys.toTypedArray(), *args)

/** Loads `rev` and `data` of many hashes in one pipelined round trip; missing records map to null. */
internal fun ValkeyConnection.readRecords(keys: List<String>): List<Pair<Long, ByteArray>?> {
    val pending = keys.map { async().hmget(it, ValkeyKeys.FIELD_REVISION, ValkeyKeys.FIELD_DATA) }
    return pending.map { future ->
        val (revision, data) = future.get().map { it.getValueOrElse(null) }
        if (revision == null || data == null) null else revision.utf8().toLong() to data
    }
}
