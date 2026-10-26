package io.ogwars.cloud.domain.permission

import java.time.Duration

/** How long a rank lasts, written like "1Y 2M 3w 4d 5h 6m 7s" or "-1" for permanent. */
sealed interface RankDuration {
    data object Permanent : RankDuration

    data class Timed(
        val duration: Duration,
    ) : RankDuration {
        init {
            require(duration.isPositive) { "A timed rank must last longer than zero" }
        }
    }

    companion object {
        private const val PERMANENT = "-1"

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

        fun parse(text: String): RankDuration {
            val trimmed = text.trim()
            if (trimmed == PERMANENT) return Permanent
            val parts = trimmed.split(Regex("\\s+")).filter(String::isNotEmpty)
            require(parts.isNotEmpty()) { "Rank duration is empty" }
            val total =
                parts.fold(Duration.ZERO) { sum, part ->
                    val unit = unitByChar[part.last()] ?: throw IllegalArgumentException("Unknown time unit in '$part'")
                    val amount = part.dropLast(1).toLongOrNull()
                    require(amount != null && amount > 0) { "Invalid amount in '$part'" }
                    sum + unit.multipliedBy(amount)
                }
            return Timed(total)
        }

        fun format(duration: RankDuration): String =
            when (duration) {
                Permanent -> {
                    PERMANENT
                }

                is Timed -> {
                    var remaining = duration.duration
                    UNITS
                        .mapNotNull { (char, unit) ->
                            val count = remaining.dividedBy(unit)
                            remaining -= unit.multipliedBy(count)
                            if (count > 0) "$count$char" else null
                        }.joinToString(" ")
                }
            }
    }
}
