package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore

/**
 * How a read model is written. An announcement overwrites, since it read PostgreSQL after the commit. A resync
 * replaces only the copy it read before PostgreSQL, so it never puts back an older state over a newer
 * announcement (D-0100).
 */
sealed interface ReadModelWrite {
    data object Overwrite : ReadModelWrite

    class Replace(
        val snapshot: ByteArray?,
    ) : ReadModelWrite

    companion object {
        /** For a resync, read before PostgreSQL. */
        fun of(
            readModels: ReadModelStore,
            kind: ReadModelKind,
            id: String,
            resync: Boolean,
        ): ReadModelWrite = if (resync) Replace(readModels.get(kind, id)) else Overwrite
    }
}

/** Writes [encoded], or deletes the read model when it is null; true if the stored copy changed. */
fun ReadModelStore.write(
    kind: ReadModelKind,
    id: String,
    encoded: ByteArray?,
    how: ReadModelWrite,
): Boolean =
    when (how) {
        ReadModelWrite.Overwrite -> {
            if (encoded == null) delete(kind, id) else put(kind, id, encoded)
            true
        }

        is ReadModelWrite.Replace -> {
            !how.snapshot.contentEquals(encoded) && replace(kind, id, how.snapshot, encoded)
        }
    }
