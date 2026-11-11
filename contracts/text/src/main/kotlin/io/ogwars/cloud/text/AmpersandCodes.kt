package io.ogwars.cloud.text

/**
 * v1's `&` colour codes as tags, for the migration of stored texts only (D-0201). Characters the tag format reads
 * (`<`, `{`, `\`) are escaped first, and a colour code resets what came before, as it did in legacy text.
 */
object AmpersandCodes {
    @JvmStatic
    fun toTags(text: String): String {
        val out = StringBuilder()
        var open = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val tag = if (char == '&') text.getOrNull(index + 1)?.lowercaseChar()?.let(CODES::get) else null
            when {
                tag == RESET -> {
                    if (open) out.append("<reset>")
                    open = false
                    index += 2
                }

                tag != null -> {
                    if (open && tag in COLORS) out.append("<reset>")
                    out.append('<').append(tag).append('>')
                    open = true
                    index += 2
                }

                else -> {
                    if (char == '<' || char == '{' || char == '\\') out.append('\\')
                    out.append(char)
                    index++
                }
            }
        }
        return out.toString()
    }

    private const val RESET = "reset"
    private val COLORS = Color.NAMED.keys
    private val CODES: Map<Char, String> =
        Color.NAMED.values.associate { it.code to it.name } +
            Decoration.entries.associate { it.code to it.tag } +
            ('r' to RESET)
}
