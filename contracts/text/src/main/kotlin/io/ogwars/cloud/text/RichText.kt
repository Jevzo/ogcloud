package io.ogwars.cloud.text

/**
 * A text in OgCloud's tag format (D-0200), parsed into a tree that the renderers turn into Adventure components,
 * legacy `§` text or plain text. The tree holds no Adventure types, so validation needs no Adventure on the classpath.
 */
class RichText internal constructor(
    internal val nodes: List<Node>,
) {
    /** The placeholders the text uses, such as `name` for `{name}`. */
    fun placeholders(): Set<String> = placeholderNodes().mapTo(mutableSetOf()) { it.name }

    internal fun placeholderNodes(): List<Node.Placeholder> = buildList { collectPlaceholders(nodes, this) }

    /** The text with the named placeholders filled in as literal text, which is never read as tags. */
    fun fill(values: Map<String, String>): RichText = insert(values.mapValues { plain(it.value) })

    fun fill(vararg values: Pair<String, String>): RichText = fill(values.toMap())

    /** The text with the named placeholders replaced by texts of their own, such as a group's prefix. */
    fun insert(values: Map<String, RichText>): RichText = RichText(insertNodes(nodes, values))

    /** This text followed by [other]; tags left open in either end with it. */
    operator fun plus(other: RichText): RichText = RichText(nodes + other.nodes)

    override fun equals(other: Any?): Boolean = other is RichText && other.nodes == nodes

    override fun hashCode(): Int = nodes.hashCode()

    override fun toString(): String = "RichText($nodes)"

    companion object {
        /** The parsed text, or every error found in it. */
        @JvmStatic
        fun parse(source: String): ParseResult = Parser(source).parse()

        /** For texts that must be valid, such as the message catalog's: the parsed text, or an exception naming the errors. */
        @JvmStatic
        fun parseValid(source: String): RichText =
            when (val result = parse(source)) {
                is ParseResult.Valid -> result.text
                is ParseResult.Invalid -> throw IllegalArgumentException("Invalid text \"$source\": ${result.errors}")
            }

        /** Plain text with no tags, as a player's name or typed chat. */
        @JvmStatic
        fun plain(text: String): RichText = RichText(if (text.isEmpty()) emptyList() else listOf(Node.Literal(text)))
    }
}

sealed interface ParseResult {
    data class Valid(
        val text: RichText,
    ) : ParseResult

    data class Invalid(
        val errors: List<TextError>,
    ) : ParseResult
}

/** What is wrong at [offset], a character index into the source. */
data class TextError(
    val offset: Int,
    val reason: String,
)

internal sealed interface Node {
    data class Literal(
        val text: String,
    ) : Node

    /** `{name}` at [offset] in the source. */
    data class Placeholder(
        val name: String,
        val offset: Int,
    ) : Node

    data class Styled(
        val style: Style,
        val children: List<Node>,
    ) : Node
}

internal sealed interface Style {
    data class Solid(
        val color: Color,
    ) : Style

    data class Gradient(
        val stops: List<Color>,
    ) : Style

    data object Rainbow : Style

    data class Decorated(
        val decoration: Decoration,
    ) : Style
}

private fun collectPlaceholders(
    nodes: List<Node>,
    into: MutableList<Node.Placeholder>,
) {
    for (node in nodes) {
        when (node) {
            is Node.Placeholder -> into += node
            is Node.Styled -> collectPlaceholders(node.children, into)
            is Node.Literal -> Unit
        }
    }
}

private fun insertNodes(
    nodes: List<Node>,
    values: Map<String, RichText>,
): List<Node> =
    nodes.flatMap { node ->
        when (node) {
            is Node.Placeholder -> values[node.name]?.nodes ?: listOf(node)
            is Node.Styled -> listOf(node.copy(children = insertNodes(node.children, values)))
            is Node.Literal -> listOf(node)
        }
    }
