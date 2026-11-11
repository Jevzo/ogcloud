package io.ogwars.cloud.text

/** One pass over the source; every error is collected, so a dashboard can show them all at once. */
internal class Parser(
    private val source: String,
) {
    /** An open tag: what it sets, and the names `</name>` may close it by. */
    private class Frame(
        val style: Style?,
        val names: Set<String>,
    ) {
        val children = mutableListOf<Node>()
        val text = StringBuilder()

        fun flush() {
            if (text.isNotEmpty()) {
                children += Node.Literal(text.toString())
                text.clear()
            }
        }
    }

    private val errors = mutableListOf<TextError>()
    private val stack = ArrayDeque<Frame>().apply { addLast(Frame(null, emptySet())) }
    private var position = 0

    fun parse(): ParseResult {
        while (position < source.length) {
            when (source[position]) {
                '\\' -> escape()
                '<' -> tag()
                '{' -> placeholder()
                else -> stack.last().text.append(source[position++])
            }
        }
        while (stack.size > 1) close()
        val root = stack.single().also(Frame::flush)
        return if (errors.isEmpty()) ParseResult.Valid(RichText(root.children)) else ParseResult.Invalid(errors)
    }

    /** `\<`, `\{` and `\\` stand for the character itself; any other backslash is literal. */
    private fun escape() {
        val next = source.getOrNull(position + 1)
        if (next == '<' || next == '{' || next == '\\') {
            stack.last().text.append(next)
            position += 2
        } else {
            stack.last().text.append('\\')
            position++
        }
    }

    private fun placeholder() {
        val match = PLACEHOLDER.matchAt(source, position)
        if (match == null) {
            stack.last().text.append('{')
            position++
            return
        }
        stack.last().flush()
        stack.last().children += Node.Placeholder(match.groupValues[1])
        position = match.range.last + 1
    }

    private fun tag() {
        val start = position
        val end = source.indexOfAny(charArrayOf('>', '<'), start + 1)
        if (end == -1 || source[end] == '<') {
            errors += TextError(start, "'<' starts no tag; write \\< for a literal <")
            stack.last().text.append('<')
            position++
            return
        }
        position = end + 1
        val content = source.substring(start + 1, end).lowercase()
        when {
            content.startsWith('/') -> closeTag(start, content.substring(1))
            content == RESET -> while (stack.size > 1) close()
            else -> openTag(start, content)
        }
    }

    private fun openTag(
        start: Int,
        content: String,
    ) {
        val opened = style(start, content) ?: return
        stack.last().flush()
        stack.addLast(Frame(opened.first, opened.second))
    }

    /** What `<content>` sets and the names that close it, or null after reporting why it sets nothing. */
    private fun style(
        start: Int,
        content: String,
    ): Pair<Style, Set<String>>? {
        val name = content.substringBefore(':')
        val arguments = if (':' in content) content.substringAfter(':').split(':') else emptyList()
        Decoration.BY_TAG[content]?.let { return Style.Decorated(it) to setOf(content) }
        Color.parse(content)?.let { return Style.Solid(it) to setOf(content, COLOR) }
        return when (name) {
            COLOR -> {
                colors(start, content, arguments, exactly = 1)?.let { Style.Solid(it.single()) to setOf(COLOR) }
            }

            GRADIENT -> {
                colors(start, content, arguments, exactly = null)?.let { Style.Gradient(it) to setOf(GRADIENT) }
            }

            RAINBOW -> {
                if (arguments.isEmpty()) {
                    Style.Rainbow to setOf(RAINBOW)
                } else {
                    fail(start, "<rainbow> takes no arguments")
                }
            }

            else -> {
                fail(start, "unknown tag <$content>")
            }
        }
    }

    /** The colours of `<color:…>` (exactly one) or `<gradient:…>` (two or more). */
    private fun colors(
        start: Int,
        content: String,
        arguments: List<String>,
        exactly: Int?,
    ): List<Color>? {
        val colors = arguments.map { Color.parse(it) ?: return fail(start, "\"$it\" in <$content> is no colour") }
        return when {
            exactly != null && colors.size != exactly -> fail(start, "<$content> takes one colour")
            exactly == null && colors.size < MIN_GRADIENT_STOPS -> fail(start, "<$content> takes two or more colours")
            else -> colors
        }
    }

    private fun closeTag(
        start: Int,
        name: String,
    ) {
        val index = stack.indexOfLast { name in it.names }
        if (index <= 0) {
            errors += TextError(start, "</$name> closes no open tag")
            return
        }
        // Tags opened inside the closed one end with it.
        while (stack.size > index) close()
    }

    private fun close() {
        val frame = stack.removeLast().also(Frame::flush)
        val parent = stack.last().also(Frame::flush)
        parent.children += Node.Styled(checkNotNull(frame.style), frame.children)
    }

    private fun fail(
        offset: Int,
        reason: String,
    ): Nothing? {
        errors += TextError(offset, reason)
        return null
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{([A-Za-z][A-Za-z0-9_]*)}")
        const val RESET = "reset"
        const val COLOR = "color"
        const val GRADIENT = "gradient"
        const val RAINBOW = "rainbow"
        const val MIN_GRADIENT_STOPS = 2
    }
}
