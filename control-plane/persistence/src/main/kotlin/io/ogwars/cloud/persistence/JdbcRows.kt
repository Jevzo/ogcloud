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
