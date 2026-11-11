package io.ogwars.cloud.text

import net.kyori.adventure.text.format.NamedTextColor

/**
 * The text in legacy `§` codes, for 1.8 and the load balancer's older clients: each colour as its nearest named colour
 * (D-0204). A colour code clears the decorations before it, so they are written again after each one. With
 * [collapseGradients], for texts 1.8 caps in length, a gradient or rainbow takes its first colour.
 */
fun RichText.toLegacy(collapseGradients: Boolean = false): String {
    val out = StringBuilder()
    var color: Char? = null
    var decorations = emptySet<Decoration>()
    for (span in spans(collapseGradients)) {
        val code = span.color?.let { legacyCode(it) }
        if (code != color || !span.decorations.containsAll(decorations)) {
            out.append(SECTION).append(code ?: RESET)
            span.decorations.forEach { out.append(SECTION).append(it.code) }
        } else {
            (span.decorations - decorations).forEach { out.append(SECTION).append(it.code) }
        }
        color = code
        decorations = span.decorations
        out.append(span.text)
    }
    return out.toString()
}

/** [color]'s nearest named colour, the one clients before 1.16 show (D-0204). */
fun legacyColor(color: Color): Color.Named =
    when (color) {
        is Color.Named -> color
        is Color.Hex -> Color.NAMED.getValue(checkNotNull(NamedTextColor.NAMES.key(color.nearestNamed())))
    }

/** The legacy code of [color]'s nearest named colour. */
fun legacyCode(color: Color): Char = legacyColor(color).code

private const val SECTION = '§'
private const val RESET = 'r'
