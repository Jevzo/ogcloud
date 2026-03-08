package io.ogwars.cloud.api.util

object TimeUtils {
    private const val PERMANENT_SENTINEL = -1L
    private const val PERMANENT_VALUE = "-1"
    private const val PERMANENT_LABEL = "permanent"

    private const val MILLIS_PER_SECOND = 1000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MILLIS_PER_WEEK = 604_800_000L
    private const val MILLIS_PER_MONTH = 2_629_800_000L
    private const val MILLIS_PER_YEAR = 31_557_600_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_WEEK = 604_800L
    private const val SECONDS_PER_MONTH = 2_629_800L
    private const val SECONDS_PER_YEAR = 31_557_600L

    private val millisPerUnit =
        mapOf(
            'Y' to MILLIS_PER_YEAR,
            'M' to MILLIS_PER_MONTH,
            'w' to MILLIS_PER_WEEK,
            'd' to MILLIS_PER_DAY,
            'h' to MILLIS_PER_HOUR,
            'm' to MILLIS_PER_MINUTE,
            's' to MILLIS_PER_SECOND,
        )

    fun parseTimeString(time: String): Long {
        if (time == PERMANENT_VALUE) {
            return PERMANENT_SENTINEL
        }

        var result = 0L
        for (part in time.split(Regex("\\s+")).filter(String::isNotBlank)) {
            val unit = part.last()
            val number = part.dropLast(1).toLong()
            val unitMillis =
                millisPerUnit[unit]
                    ?: throw IllegalArgumentException("Unknown time unit: $unit")
            result += number * unitMillis
        }

        return result
    }

    fun formatMillis(millis: Long): String {
        if (millis == PERMANENT_SENTINEL) {
            return PERMANENT_LABEL
        }

        var remaining = millis / MILLIS_PER_SECOND
        val years = remaining / SECONDS_PER_YEAR
        remaining %= SECONDS_PER_YEAR
        val months = remaining / SECONDS_PER_MONTH
        remaining %= SECONDS_PER_MONTH
        val weeks = remaining / SECONDS_PER_WEEK
        remaining %= SECONDS_PER_WEEK
        val days = remaining / SECONDS_PER_DAY
        remaining %= SECONDS_PER_DAY
        val hours = remaining / SECONDS_PER_HOUR
        remaining %= SECONDS_PER_HOUR
        val minutes = remaining / SECONDS_PER_MINUTE
        val seconds = remaining % SECONDS_PER_MINUTE

        return buildString {
            if (years > 0) append("${years}Y ")
            if (months > 0) append("${months}M ")
            if (weeks > 0) append("${weeks}w ")
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (seconds > 0) append("${seconds}s ")
        }.trim().ifEmpty { "0s" }
    }
}
