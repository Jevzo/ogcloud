package io.ogwars.cloud.node.core.text

/** `&` colour codes, as every catalog text and network setting writes them. */
object ColorCodes {
    private const val COLORS = "0123456789abcdef"

    /** The last colour set in [text], e.g. 'c' for "&7Name &c", or null when it sets none. */
    @JvmStatic
    fun lastColor(text: String): Char? =
        text
            .windowed(2)
            .lastOrNull { it[0] == '&' && it[1].lowercaseChar() in COLORS }
            ?.get(1)
            ?.lowercaseChar()
}
