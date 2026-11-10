package io.ogwars.cloud.domain.common

import java.math.BigDecimal

/** Kubernetes memory quantities such as "1536Mi", "2G" or "1e9", in bytes. */
object MemoryQuantity {
    // A number with a binary or decimal suffix, or with a decimal exponent, never both.
    private val PATTERN = Regex("^(\\d+(?:\\.\\d+)?)(?:(Ki|Mi|Gi|Ti|Pi|Ei|k|M|G|T|P|E)|[eE](-?\\d+))?$")
    private val FACTORS =
        mapOf(
            "Ki" to BigDecimal.TWO.pow(10),
            "Mi" to BigDecimal.TWO.pow(20),
            "Gi" to BigDecimal.TWO.pow(30),
            "Ti" to BigDecimal.TWO.pow(40),
            "Pi" to BigDecimal.TWO.pow(50),
            "Ei" to BigDecimal.TWO.pow(60),
            "k" to BigDecimal.TEN.pow(3),
            "M" to BigDecimal.TEN.pow(6),
            "G" to BigDecimal.TEN.pow(9),
            "T" to BigDecimal.TEN.pow(12),
            "P" to BigDecimal.TEN.pow(15),
            "E" to BigDecimal.TEN.pow(18),
        )
    private val BINARY_UNITS = listOf("Ei", "Pi", "Ti", "Gi", "Mi", "Ki")

    /**
     * The quantity in bytes, or null when it is not a whole number of bytes in a form Kubernetes knows, or more than
     * a Long holds.
     */
    fun bytes(quantity: String): Long? {
        val (number, unit, exponent) = PATTERN.matchEntire(quantity)?.destructured ?: return null
        val factor =
            if (exponent.isEmpty()) {
                FACTORS[unit] ?: BigDecimal.ONE
            } else {
                BigDecimal.ONE.scaleByPowerOfTen(
                    exponent.toInt(),
                )
            }
        val bytes = BigDecimal(number) * factor
        return try {
            bytes.toBigIntegerExact().longValueExact()
        } catch (_: ArithmeticException) {
            // A fraction of a byte, or past a Long.
            null
        }
    }

    /** The bytes in the largest binary unit that divides them, e.g. 1536Mi. */
    fun format(bytes: Long): String {
        val unit = BINARY_UNITS.firstOrNull { bytes % FACTORS.getValue(it).toLong() == 0L } ?: return bytes.toString()
        return "${bytes / FACTORS.getValue(unit).toLong()}$unit"
    }
}
