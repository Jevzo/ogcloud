package io.ogwars.cloud.persistence

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

// Small ResultSet helpers shared by the JdbcClient-based stores.

internal fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)

internal fun ResultSet.uuidOrNull(column: String): UUID? = getObject(column, UUID::class.java)

internal fun ResultSet.instant(column: String): Instant = getTimestamp(column).toInstant()

internal fun ResultSet.instantOrNull(column: String): Instant? = getTimestamp(column)?.toInstant()

internal fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

/** Sorts before every UUID, so an ascending first page starts at the first row. */
internal val MIN_ID = UUID(0, 0)

/** Sorts after every UUID, so a descending first page starts at the newest row. */
internal val MAX_ID = UUID(-1L, -1L)
