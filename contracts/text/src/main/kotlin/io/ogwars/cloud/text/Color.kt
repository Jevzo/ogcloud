package io.ogwars.cloud.text

/** A colour in a text: one of Minecraft's 16 named colours, or any RGB value. */
sealed interface Color {
    val rgb: Int

    data class Named(
        val name: String,
        override val rgb: Int,
        /** Its legacy code, as in `§c` for red. */
        val code: Char,
    ) : Color

    data class Hex(
        override val rgb: Int,
    ) : Color

    companion object {
        /** Minecraft's named colours, by the tag names MiniMessage uses. */
        val NAMED: Map<String, Named> =
            listOf(
                Named("black", 0x000000, '0'),
                Named("dark_blue", 0x0000AA, '1'),
                Named("dark_green", 0x00AA00, '2'),
                Named("dark_aqua", 0x00AAAA, '3'),
                Named("dark_red", 0xAA0000, '4'),
                Named("dark_purple", 0xAA00AA, '5'),
                Named("gold", 0xFFAA00, '6'),
                Named("gray", 0xAAAAAA, '7'),
                Named("dark_gray", 0x555555, '8'),
                Named("blue", 0x5555FF, '9'),
                Named("green", 0x55FF55, 'a'),
                Named("aqua", 0x55FFFF, 'b'),
                Named("red", 0xFF5555, 'c'),
                Named("light_purple", 0xFF55FF, 'd'),
                Named("yellow", 0xFFFF55, 'e'),
                Named("white", 0xFFFFFF, 'f'),
            ).associateBy(Named::name)

        /** A named colour or `#rrggbb`, or null when [spec] is neither. */
        internal fun parse(spec: String): Color? {
            NAMED[spec]?.let { return it }
            if (spec.length != HEX_LENGTH || spec[0] != '#') return null
            return spec.substring(1).toIntOrNull(HEX_RADIX)?.let(::Hex)
        }

        private const val HEX_LENGTH = 7
        private const val HEX_RADIX = 16
    }
}

/** The decorations a text may set, with their legacy codes. */
enum class Decoration(
    val tag: String,
    val code: Char,
) {
    BOLD("bold", 'l'),
    ITALIC("italic", 'o'),
    UNDERLINED("underlined", 'n'),
    STRIKETHROUGH("strikethrough", 'm'),
    OBFUSCATED("obfuscated", 'k'),
    ;

    companion object {
        internal val BY_TAG: Map<String, Decoration> = entries.associateBy(Decoration::tag)
    }
}
