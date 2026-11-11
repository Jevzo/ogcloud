package io.ogwars.cloud.text

import kotlin.math.roundToInt

/** A stretch of text with one colour (null: the default) and one set of decorations, as every renderer writes it. */
internal data class Run(
    val text: String,
    val color: Color?,
    val decorations: Set<Decoration>,
)

/**
 * The text's runs. Gradients and rainbows colour each code point, counted across their whole span with placeholders
 * as filled in; an unfilled placeholder is shown as written. With [collapseGradients] each takes its first colour.
 */
internal fun RichText.runs(collapseGradients: Boolean = false): List<Run> {
    val glyphs = mutableListOf<Glyph>()
    layout(nodes, null, emptySet(), collapseGradients, glyphs)
    val runs = mutableListOf<Run>()
    val text = StringBuilder()
    var current: Pair<Color?, Set<Decoration>>? = null
    for (glyph in glyphs) {
        val style = glyph.color() to glyph.decorations
        if (style != current && current != null) {
            runs += Run(text.toString(), current.first, current.second)
            text.clear()
        }
        current = style
        text.append(glyph.text)
    }
    current?.let { runs += Run(text.toString(), it.first, it.second) }
    return runs
}

/** The colour in effect at the end, which a typed chat message continues in; null for the default. */
fun RichText.endColor(): Color? = runs().lastOrNull()?.color

/** The colour of the first character, which gives a team its nearest named colour; null for the default. */
fun RichText.firstColor(): Color? = runs().firstOrNull()?.color

/** The text without any styling. */
fun RichText.toPlain(): String = runs().joinToString("") { it.text }

/** Where a glyph's colour comes from: one colour, or a scale it takes its share of. */
private sealed interface Paint {
    data class Fixed(
        val color: Color,
    ) : Paint

    class Scale(
        val style: Style,
    ) : Paint {
        var size = 0

        fun at(index: Int): Color =
            when (style) {
                is Style.Gradient -> gradient(style.stops, index, size)
                Style.Rainbow -> rainbow(index, size)
                else -> error("a scale is a gradient or a rainbow, not $style")
            }
    }
}

private class Glyph(
    val text: String,
    val paint: Paint?,
    val index: Int,
    val decorations: Set<Decoration>,
) {
    fun color(): Color? =
        when (paint) {
            null -> null
            is Paint.Fixed -> paint.color
            is Paint.Scale -> paint.at(index)
        }
}

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
                glyphs("{${node.name}}", paint, decorations, into)
            }

            is Node.Styled -> {
                when (val style = node.style) {
                    is Style.Decorated -> {
                        layout(node.children, paint, decorations + style.decoration, collapse, into)
                    }

                    is Style.Solid -> {
                        layout(node.children, Paint.Fixed(style.color), decorations, collapse, into)
                    }

                    is Style.Gradient, Style.Rainbow -> {
                        val inner = if (collapse) Paint.Fixed(firstColor(style)) else Paint.Scale(style)
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
) {
    var offset = 0
    while (offset < text.length) {
        val end = text.offsetByCodePoints(offset, 1)
        val index = if (paint is Paint.Scale) paint.size++ else 0
        into += Glyph(text.substring(offset, end), paint, index, decorations)
        offset = end
    }
}

private fun firstColor(style: Style): Color =
    when (style) {
        is Style.Gradient -> style.stops.first()
        else -> rainbow(0, 1)
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
    val hue = index.toDouble() / size * HUE_SECTORS
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

private const val HUE_SECTORS = 6
private const val CHANNEL_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
