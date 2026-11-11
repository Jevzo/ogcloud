package io.ogwars.cloud.text

import net.kyori.adventure.text.format.NamedTextColor

/**
 * The text in legacy `§` codes, for 1.8 and the load balancer's older clients: each colour as its nearest named colour
 * (D-0204). A colour code clears the decorations before it, so they are written again after each one. With
 * [collapseGradients] a gradient or rainbow takes its first colour; [maxLength] cuts the text, codes included, and
 * never leaves codes without the text they colour.
 */
fun RichText.toLegacy(
    collapseGradients: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
): String = legacyRuns(collapseGradients, maxLength).joinToString("") { it.codes + it.span.text }

/** The spans as clients before 1.16 show them: what [toLegacy] writes, in nearest named colours (D-0204). */
fun RichText.legacySpans(
    collapseGradients: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
): List<Span> = legacyRuns(collapseGradients, maxLength).map { it.span }

/**
 * A 1.8 team prefix: [prefix] with gradients collapsed, then the code of [nameColor] that colours the name after it,
 * within the 16 characters 1.8 allows; the prefix is cut first, so the name keeps its colour.
 */
fun legacyTeamPrefix(
    prefix: RichText,
    nameColor: Color?,
): String {
    val name = nameColor?.legacyCode().orEmpty()
    return prefix.toLegacy(TextField.TAB_PREFIX.collapsesOnLegacy, LEGACY_TEAM_PREFIX_LENGTH - name.length) + name
}

/** The nearest named colour, the one clients before 1.16 show (D-0204). */
fun Color.nearestLegacy(): NamedColor =
    when (this) {
        is NamedColor -> this
        is Color.Hex -> checkNotNull(NamedColor.byTag(checkNotNull(NamedTextColor.NAMES.key(nearestNamed()))))
    }

/** The legacy code of the nearest named colour, as `§c`. */
fun Color.legacyCode(): String = "$SECTION_SIGN${nearestLegacy().code}"

/** The legacy code that ends every colour and decoration. */
const val LEGACY_RESET = "${SECTION_SIGN}r"

/** 1.8's team prefixes hold 16 characters, codes included. */
const val LEGACY_TEAM_PREFIX_LENGTH = 16

/** A legacy code, `§` and its letter. */
internal const val LEGACY_CODE_LENGTH = 2

/** One span and the codes written before it. */
private class LegacyRun(
    val codes: String,
    val span: Span,
)

private fun RichText.legacyRuns(
    collapse: Boolean,
    maxLength: Int,
): List<LegacyRun> {
    val runs = mutableListOf<LegacyRun>()
    var color: NamedColor? = null
    var decorations = emptySet<Decoration>()
    var room = maxLength
    for (span in spans(collapse)) {
        val named = span.color?.nearestLegacy()
        val restart = named != color || !span.decorations.containsAll(decorations)
        val codes =
            buildString {
                if (restart) append(named?.legacyCode() ?: LEGACY_RESET)
                (if (restart) span.decorations else span.decorations - decorations).forEach {
                    append(SECTION_SIGN).append(it.code)
                }
            }
        if (codes.length >= room) break
        val text = span.text.cutTo(room - codes.length)
        val previous = runs.lastOrNull()
        runs +=
            if (codes.isEmpty() && previous != null) {
                runs.removeLast()
                LegacyRun(previous.codes, Span(previous.span.text + text, named, span.decorations))
            } else {
                LegacyRun(codes, Span(text, named, span.decorations))
            }
        room -= codes.length + text.length
        color = named
        decorations = span.decorations
        if (text.length < span.text.length) break
    }
    return runs
}

/** At most [length] characters, without half a surrogate pair at the end. */
private fun String.cutTo(length: Int): String {
    val cut = take(length)
    return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
}
