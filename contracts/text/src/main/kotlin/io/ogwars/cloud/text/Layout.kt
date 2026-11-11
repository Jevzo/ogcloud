package io.ogwars.cloud.text

import kotlin.math.roundToInt

/** A stretch of text with one colour (null: the default) and one set of decorations, as every renderer writes it. */
@ConsistentCopyVisibility
data class Span internal constructor(
    val text: String,
    val color: Color?,
    val decorations: Set<Decoration>,
)

/**
 * The text's spans, none of them empty. Gradients and rainbows colour each code point, counted over the characters
 * they colour with placeholders as filled in; an unfilled placeholder is shown as written. With [collapseGradients]
 * each takes its first colour. A '§' is dropped, so no literal or value adds legacy codes (D-0206).
 */
fun RichText.spans(collapseGradients: Boolean = false): List<Span> {
    val spans = mutableListOf<Span>()
    val text = StringBuilder()
    var current: Pair<Color?, Set<Decoration>>? = null
    for (glyph in glyphs(collapseGradients)) {
        if (glyph.text.isEmpty()) continue
        val style = glyph.color() to glyph.decorations
        if (style != current && current != null) {
            spans += Span(text.toString(), current.first, current.second)
            text.clear()
        }
        current = style
        text.append(glyph.text)
    }
    current?.let { spans += Span(text.toString(), it.first, it.second) }
    return spans
}

/**
 * The colour in effect at the end, which a typed chat message continues in: the last character's, or an empty colour
 * tag's at the very end; null, the default, once a closing tag or `<reset>` has ended every colour.
 */
fun RichText.endColor(): Color? = glyphs(collapse = false).lastOrNull()?.color()

/** The colour [placeholder] is shown in, its first character's under a gradient; null for the default (D-0206). */
fun RichText.colorOf(placeholder: String): Color? =
    glyphs(collapse = false).firstOrNull { it.placeholder == placeholder }?.color()

/** The text without any styling. */
fun RichText.toPlain(): String = spans().joinToString("") { it.text }

/** Where a glyph's colour comes from: one colour, or a scale it takes its share of. */
private sealed interface Paint {
    data class Fixed(
        val color: Color,
    ) : Paint

    class Scale(
        val style: Style.Scaled,
    ) : Paint {
        /** Known only once the whole text is laid out, so glyphs read their colour afterwards. */
        var size = 0

        fun at(index: Int): Color = style.colorAt(index, size)
    }
}

/** One character, or an empty marker of the colour in effect at its place; [placeholder] names its source. */
private class Glyph(
    val text: String,
    val paint: Paint?,
    val index: Int,
    val decorations: Set<Decoration>,
    val placeholder: String? = null,
) {
    fun color(): Color? =
        when (paint) {
            null -> null
            is Paint.Fixed -> paint.color
            is Paint.Scale -> paint.at(index)
        }
}

private fun RichText.glyphs(collapse: Boolean): List<Glyph> =
    mutableListOf<Glyph>().also { layout(nodes, null, emptySet(), collapse, it) }

private fun layout(
    nodes: List<Node>,
    paint: Paint?,
    decorations: Set<Decoration>,
    collapse: Boolean,
    into: MutableList<Glyph>,
) {
    for (node in nodes) {
        when (node) {
            is Node.Literal -> {
                glyphs(node.text, paint, decorations, into)
            }

            is Node.Placeholder -> {
                glyphs("{${node.name}}", paint, decorations, into, node.name)
            }

            Node.End -> {
                // Marks the colour in effect here; under a scale, the character's before it.
                val index = if (paint is Paint.Scale) maxOf(paint.size - 1, 0) else 0
                into += Glyph("", paint, index, decorations)
            }

            is Node.Styled -> {
                when (val style = node.style) {
                    is Style.Decorated -> {
                        layout(node.children, paint, decorations + style.decoration, collapse, into)
                    }

                    is Style.Solid -> {
                        val before = into.size
                        layout(node.children, Paint.Fixed(style.color), decorations, collapse, into)
                        // Kept as an empty glyph, so an empty colour tag at the very end sets the colour that follows.
                        if (into.size == before) into += Glyph("", Paint.Fixed(style.color), 0, decorations)
                    }

                    is Style.Scaled -> {
                        // Collapsed, a scale shows what a lone character gets: a gradient's first stop, the rainbow's red.
                        val inner = if (collapse) Paint.Fixed(style.colorAt(0, 1)) else Paint.Scale(style)
                        layout(node.children, inner, decorations, collapse, into)
                    }
                }
            }
        }
    }
}

private fun glyphs(
    text: String,
    paint: Paint?,
    decorations: Set<Decoration>,
    into: MutableList<Glyph>,
    placeholder: String? = null,
) {
    var offset = 0
    while (offset < text.length) {
        val end = text.offsetByCodePoints(offset, 1)
        if (text[offset] != SECTION_SIGN) {
            val index = if (paint is Paint.Scale) paint.size++ else 0
            into += Glyph(text.substring(offset, end), paint, index, decorations, placeholder)
        }
        offset = end
    }
}

private fun Style.Scaled.colorAt(
    index: Int,
    size: Int,
): Color =
    when (this) {
        is Style.Gradient -> gradient(stops, index, size)
        Style.Rainbow -> rainbow(index, size)
    }

/** Linear in RGB between neighbouring stops, from the first character's first stop to the last's last stop. */
private fun gradient(
    stops: List<Color>,
    index: Int,
    size: Int,
): Color {
    if (size <= 1) return stops.first()
    val position = index.toDouble() / (size - 1) * (stops.size - 1)
    val segment = minOf(position.toInt(), stops.size - 2)
    return Color.Hex(lerp(stops[segment].rgb, stops[segment + 1].rgb, position - segment))
}

/** Full saturation and brightness, the hue going once round the colour wheel over the span. */
private fun rainbow(
    index: Int,
    size: Int,
): Color {
    val hue = index.toDouble() / maxOf(size, 1) * HUE_SECTORS
    val sector = hue.toInt() % HUE_SECTORS
    val rising = (hue - hue.toInt()) * CHANNEL_MASK
    val up = rising.roundToInt()
    val down = CHANNEL_MASK - up
    val (red, green, blue) =
        when (sector) {
            0 -> Triple(CHANNEL_MASK, up, 0)
            1 -> Triple(down, CHANNEL_MASK, 0)
            2 -> Triple(0, CHANNEL_MASK, up)
            3 -> Triple(0, down, CHANNEL_MASK)
            4 -> Triple(up, 0, CHANNEL_MASK)
            else -> Triple(CHANNEL_MASK, 0, down)
        }
    return Color.Hex(red shl RED_SHIFT or (green shl GREEN_SHIFT) or blue)
}

private fun lerp(
    from: Int,
    to: Int,
    fraction: Double,
): Int {
    fun channel(shift: Int): Int {
        val a = from shr shift and CHANNEL_MASK
        val b = to shr shift and CHANNEL_MASK
        return (a + (b - a) * fraction).roundToInt() shl shift
    }
    return channel(RED_SHIFT) or channel(GREEN_SHIFT) or channel(0)
}

internal const val SECTION_SIGN = '§'
private const val HUE_SECTORS = 6
private const val CHANNEL_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
