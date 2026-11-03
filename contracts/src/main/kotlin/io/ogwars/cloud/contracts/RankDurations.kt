package io.ogwars.cloud.contracts

import java.time.Duration

/**
 * Rank durations as admins type them, in the dashboard and in game, as in v1: "1Y 2M 3w 4d 5h 6m 7s", or "-1" for
 * a permanent rank, which is null.
 */
object RankDurations {
    const val PERMANENT = "-1"

    /** Calendar-average units, as in v1: a month is 30.4375 days and a year 365.25 days. */
    private val UNITS: List<Pair<Char, Duration>> =
        listOf(
            'Y' to Duration.ofMinutes(525_960),
            'M' to Duration.ofMinutes(43_830),
            'w' to Duration.ofDays(7),
            'd' to Duration.ofDays(1),
            'h' to Duration.ofHours(1),
            'm' to Duration.ofMinutes(1),
            's' to Duration.ofSeconds(1),
        )
    private val unitByChar = UNITS.toMap()

    /** Null for a permanent rank; throws [IllegalArgumentException] for malformed text. */
    @JvmStatic
    fun parse(text: String): Duration? {
        val trimmed = text.trim()
        if (trimmed == PERMANENT) return null
        val parts = trimmed.split(Regex("\\s+")).filter(String::isNotEmpty)
        require(parts.isNotEmpty()) { "Rank duration is empty" }
        return try {
            parts.fold(Duration.ZERO) { sum, part -> sum + part(part) }
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Rank duration '$text' is too long", e)
        }
    }

    @JvmStatic
    fun format(duration: Duration?): String {
        if (duration == null) return PERMANENT
        var remaining = duration
        return UNITS
            .mapNotNull { (char, unit) ->
                val count = remaining.dividedBy(unit)
                remaining -= unit.multipliedBy(count)
                if (count > 0) "$count$char" else null
            }.joinToString(" ")
    }

    private fun part(text: String): Duration {
        val unit = unitByChar[text.last()] ?: throw IllegalArgumentException("Unknown time unit in '$text'")
        val amount = text.dropLast(1).toLongOrNull()
        require(amount != null && amount > 0) { "Invalid amount in '$text'" }
        return unit.multipliedBy(amount)
    }
}
