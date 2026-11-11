package io.ogwars.cloud.text

/**
 * A text in OgCloud's tag format (D-0200) as a tree without Adventure types, so checking a text needs no Adventure;
 * two texts are equal when their trees are.
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

    /** This text followed by [other]; a tag left open in either text ends with that text. */
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

        /**
         * A text read from storage, which is checked when written but may predate a rule (D-0205): one that does not
         * parse, or is longer than [TextField.MAX_LENGTH], is shown as written and [onInvalid] learns why.
         */
        @JvmStatic
        fun stored(
            source: String,
            onInvalid: (List<TextError>) -> Unit,
        ): RichText {
            if (source.length > TextField.MAX_LENGTH) {
                onInvalid(listOf(tooLong(source)))
                return plain(source)
            }
            return when (val result = parse(source)) {
                is ParseResult.Valid -> {
                    result.text
                }

                is ParseResult.Invalid -> {
                    onInvalid(result.errors)
                    plain(source)
                }
            }
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
    ) : ParseResult {
        init {
            require(errors.isNotEmpty()) { "An invalid text has at least one error" }
        }
    }
}

/** What is wrong with a text, so a dashboard can say it in its own language (D-0205). */
enum class TextErrorCode {
    /** A tag OgCloud does not know, such as `<frog>`. */
    UNKNOWN_TAG,

    /** A `<` that starts no tag. */
    LONE_ANGLE,

    /** A colour that is neither named nor `#rrggbb`. */
    BAD_COLOUR,

    /** `<color:…>` with other than one colour. */
    COLOUR_COUNT,

    /** A gradient with fewer than two colours. */
    GRADIENT_STOPS,

    /** Arguments on a tag that takes none, as `<rainbow:2>`. */
    TAG_ARGUMENTS,

    /** A closing tag that closes nothing. */
    UNMATCHED_CLOSE,

    /** A placeholder the text does not offer. */
    UNKNOWN_PLACEHOLDER,

    /** A placeholder the text must show, as `{name}` in a name format. */
    MISSING_PLACEHOLDER,

    /** A legacy `§` code, which would bypass the tags (D-0206). */
    SECTION_SIGN,

    /** More than [TextField.MAX_LENGTH] characters. */
    TOO_LONG,
}

/**
 * What is wrong in a text: [length] characters from [offset], both in UTF-16 code units as JavaScript counts them,
 * and an English [reason] for logs.
 */
data class TextError(
    val code: TextErrorCode,
    val offset: Int,
    val length: Int,
    val reason: String,
) {
    init {
        require(offset >= 0 && length >= 0) { "An error's offset and length are not negative" }
    }
}

/** The errors in English, each with where it is, for logs and in-game replies. */
fun List<TextError>.describe(): String = joinToString("; ") { "${it.reason} (at character ${it.offset})" }

internal fun tooLong(source: String): TextError =
    TextError(
        TextErrorCode.TOO_LONG,
        TextField.MAX_LENGTH,
        source.length - TextField.MAX_LENGTH,
        "a text holds at most ${TextField.MAX_LENGTH} characters",
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

    /** Where a closing tag or `<reset>` ended the tags around it, so what follows starts outside them. */
    data object End : Node
}

internal sealed interface Style {
    data class Solid(
        val color: Color,
    ) : Style

    /** Colours each character by its place in the text it spans. */
    sealed interface Scaled : Style

    data class Gradient(
        val stops: List<Color>,
    ) : Scaled {
        init {
            require(stops.size >= MIN_GRADIENT_STOPS) { "A gradient has at least $MIN_GRADIENT_STOPS stops" }
        }
    }

    data object Rainbow : Scaled

    data class Decorated(
        val decoration: Decoration,
    ) : Style
}

internal const val MIN_GRADIENT_STOPS = 2

private fun collectPlaceholders(
    nodes: List<Node>,
    into: MutableList<Node.Placeholder>,
) {
    for (node in nodes) {
        when (node) {
            is Node.Placeholder -> into += node
            is Node.Styled -> collectPlaceholders(node.children, into)
            is Node.Literal, Node.End -> Unit
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
            is Node.Literal, Node.End -> listOf(node)
        }
    }
