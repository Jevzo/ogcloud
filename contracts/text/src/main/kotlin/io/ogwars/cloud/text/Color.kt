package io.ogwars.cloud.text

/**
 * A colour in a text: one of Minecraft's 16 named colours, or any RGB value. A named colour and a hex colour of the
 * same RGB stay different values, since their JSON differs.
 */
sealed interface Color {
    val rgb: Int

    /** `#rrggbb`, as chat-component JSON and the dashboard write it. */
    val hex: String get() = "#%06x".format(rgb)

    data class Hex(
        override val rgb: Int,
    ) : Color {
        init {
            require(rgb in 0..MAX_RGB) { "RGB value $rgb is outside 0 to 0xFFFFFF" }
        }
    }

    companion object {
        /** A named colour or `#rrggbb`, or null when [spec] is neither. */
        internal fun parse(spec: String): Color? =
            NamedColor.byTag(spec) ?: if (HEX.matches(spec)) Hex(spec.substring(1).toInt(HEX_RADIX)) else null

        private val HEX = Regex("#[0-9a-fA-F]{6}")
        private const val HEX_RADIX = 16
        private const val MAX_RGB = 0xFFFFFF
    }
}

/** Minecraft's named colours, with the tag names MiniMessage uses and their legacy codes, as in `§c` for red. */
enum class NamedColor(
    override val rgb: Int,
    val code: Char,
) : Color {
    BLACK(0x000000, '0'),
    DARK_BLUE(0x0000AA, '1'),
    DARK_GREEN(0x00AA00, '2'),
    DARK_AQUA(0x00AAAA, '3'),
    DARK_RED(0xAA0000, '4'),
    DARK_PURPLE(0xAA00AA, '5'),
    GOLD(0xFFAA00, '6'),
    GRAY(0xAAAAAA, '7'),
    DARK_GRAY(0x555555, '8'),
    BLUE(0x5555FF, '9'),
    GREEN(0x55FF55, 'a'),
    AQUA(0x55FFFF, 'b'),
    RED(0xFF5555, 'c'),
    LIGHT_PURPLE(0xFF55FF, 'd'),
    YELLOW(0xFFFF55, 'e'),
    WHITE(0xFFFFFF, 'f'),
    ;

    /** Its tag name, such as `dark_red`. */
    val tag: String = name.lowercase()

    companion object {
        private val BY_TAG = entries.associateBy(NamedColor::tag)

        @JvmStatic
        fun byTag(tag: String): NamedColor? = BY_TAG[tag]
    }
}

/** The decorations a text may set, with their legacy codes. */
enum class Decoration(
    val code: Char,
) {
    BOLD('l'),
    ITALIC('o'),
    UNDERLINED('n'),
    STRIKETHROUGH('m'),
    OBFUSCATED('k'),
    ;

    /** Its tag name, such as `bold`. */
    val tag: String = name.lowercase()

    companion object {
        internal val BY_TAG: Map<String, Decoration> = entries.associateBy(Decoration::tag)
    }
}
