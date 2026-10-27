package io.ogwars.cloud.api.web

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

/** One page of a list ordered by UUIDv7 id; [nextCursor] is null on the last page. */
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
)

/** Opaque cursors over UUIDv7 ids, which sort by creation time (D-0064), so pages stay stable while rows are added. */
object Cursors {
    const val DEFAULT_LIMIT = 50
    const val MAX_LIMIT = 200
    private const val UUID_BYTES = 16

    fun encode(id: UUID): String {
        val bytes =
            ByteBuffer
                .allocate(
                    UUID_BYTES,
                ).putLong(id.mostSignificantBits)
                .putLong(id.leastSignificantBits)
                .array()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(cursor: String): UUID {
        val bytes =
            try {
                Base64.getUrlDecoder().decode(cursor)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid cursor", e)
            }
        require(bytes.size == UUID_BYTES) { "Invalid cursor" }
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    fun limit(requested: Int?): Int {
        val limit = requested ?: DEFAULT_LIMIT
        require(limit in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
        return limit
    }

    /** Builds a page from up to limit + 1 rows: the extra row only tells whether another page exists. */
    fun <T> page(
        rows: List<T>,
        limit: Int,
        id: (T) -> UUID,
    ): Page<T> {
        val items = rows.take(limit)
        return Page(items, if (rows.size > limit) encode(id(items.last())) else null)
    }
}
