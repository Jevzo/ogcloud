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
    for (run in runs(collapseGradients)) {
        val code = run.color?.let { legacyCode(it) }
        if (code != color || !run.decorations.containsAll(decorations)) {
            out.append(SECTION).append(code ?: RESET)
            run.decorations.forEach { out.append(SECTION).append(it.code) }
        } else {
            (run.decorations - decorations).forEach { out.append(SECTION).append(it.code) }
        }
        color = code
        decorations = run.decorations
        out.append(run.text)
    }
    return out.toString()
}

/** The legacy code of [color]'s nearest named colour. */
fun legacyCode(color: Color): Char =
    when (color) {
        is Color.Named -> color.code
        is Color.Hex -> checkNotNull(Color.NAMED[NamedTextColor.NAMES.key(color.nearestNamed())]).code
    }

private const val SECTION = '§'
private const val RESET = 'r'
