package io.ogwars.cloud.domain.common

import java.math.BigDecimal

/** Kubernetes memory quantities such as "1536Mi" or "2G", in bytes. */
object MemoryQuantity {
    private val PATTERN = Regex("^(\\d+(?:\\.\\d+)?)(Ki|Mi|Gi|Ti|k|M|G|T)?$")
    private val FACTORS =
        mapOf(
            "Ki" to (1L shl 10),
            "Mi" to (1L shl 20),
            "Gi" to (1L shl 30),
            "Ti" to (1L shl 40),
            "k" to 1_000L,
            "M" to 1_000_000L,
            "G" to 1_000_000_000L,
            "T" to 1_000_000_000_000L,
        )
    private val BINARY_UNITS = listOf("Ti", "Gi", "Mi", "Ki")

    /** The quantity in bytes, or null when it is not a whole number of bytes in a unit Kubernetes knows. */
    fun bytes(quantity: String): Long? {
        val (number, unit) = PATTERN.matchEntire(quantity)?.destructured ?: return null
        val bytes = BigDecimal(number) * BigDecimal.valueOf(FACTORS[unit] ?: 1L)
        return bytes.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toLong()
    }

    /** The bytes in the largest binary unit that divides them, e.g. 1536Mi. */
    fun format(bytes: Long): String {
        val unit = BINARY_UNITS.firstOrNull { bytes % FACTORS.getValue(it) == 0L } ?: return bytes.toString()
        return "${bytes / FACTORS.getValue(unit)}$unit"
    }
}
