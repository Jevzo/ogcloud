package io.ogwars.cloud.text

/** One pass over the source; every tag error is collected, so a dashboard can show them all at once. */
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
        // Tags still open run to the end of the text.
        closeTo(1)
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
        stack.last().children += Node.Placeholder(match.groupValues[1], position)
        position = match.range.last + 1
    }

    private fun tag() {
        val start = position
        val end = source.indexOfAny(charArrayOf('>', '<'), start + 1)
        if (end == -1 || source[end] == '<') {
            fail(TextErrorCode.LONE_ANGLE, start, 1, "'<' starts no tag; write \\< for a literal <")
            stack.last().text.append('<')
            position++
            return
        }
        position = end + 1
        val tag = Tag(start, end - start + 1, source.substring(start + 1, end).lowercase())
        when {
            tag.content.startsWith('/') -> closeTag(tag, tag.content.substring(1))
            tag.content == RESET -> closeExplicitly(1)
            else -> openTag(tag)
        }
    }

    /** `<content>` at [start], [length] characters long with its angle brackets. */
    private class Tag(
        val start: Int,
        val length: Int,
        val content: String,
    ) {
        val name: String get() = content.substringBefore(':')
        val arguments: List<String> get() = if (':' in content) content.substringAfter(':').split(':') else emptyList()
    }

    private fun openTag(tag: Tag) {
        val frame = frame(tag) ?: return
        stack.last().flush()
        stack.addLast(frame)
    }

    /** What `<content>` opens, or null after reporting why it opens nothing. */
    private fun frame(tag: Tag): Frame? {
        Decoration.BY_TAG[tag.content]?.let { return Frame(Style.Decorated(it), setOf(tag.content)) }
        Color.parse(tag.content)?.let { return Frame(Style.Solid(it), setOf(tag.content, COLOR)) }
        return when (tag.name) {
            COLOR -> {
                colors(tag)?.let {
                    if (it.size == 1) {
                        Frame(Style.Solid(it.single()), setOf(COLOR))
                    } else {
                        fail(TextErrorCode.COLOUR_COUNT, tag, "<${tag.content}> takes one colour")
                    }
                }
            }

            GRADIENT -> {
                colors(tag)?.let {
                    if (it.size >= MIN_GRADIENT_STOPS) {
                        Frame(Style.Gradient(it), setOf(GRADIENT))
                    } else {
                        fail(TextErrorCode.GRADIENT_STOPS, tag, "<${tag.content}> takes two or more colours")
                    }
                }
            }

            RAINBOW -> {
                if (tag.arguments.isEmpty()) {
                    Frame(Style.Rainbow, setOf(RAINBOW))
                } else {
                    fail(TextErrorCode.TAG_ARGUMENTS, tag, "<rainbow> takes no arguments")
                }
            }

            else -> {
                fail(TextErrorCode.UNKNOWN_TAG, tag, "unknown tag <${tag.content}>")
            }
        }
    }

    private fun colors(tag: Tag): List<Color>? =
        tag.arguments.map {
            Color.parse(it) ?: return fail(TextErrorCode.BAD_COLOUR, tag, "\"$it\" in <${tag.content}> is no colour")
        }

    private fun closeTag(
        tag: Tag,
        name: String,
    ) {
        val index = stack.indexOfLast { name in it.names }
        if (index <= 0) {
            fail(TextErrorCode.UNMATCHED_CLOSE, tag, "</$name> closes no open tag")
            return
        }
        // Tags opened inside the closed one end with it.
        closeExplicitly(index)
    }

    /** Closes the tags above [depth] where the source says so, which [Node.End] marks for what follows. */
    private fun closeExplicitly(depth: Int) {
        if (stack.size <= depth) return
        closeTo(depth)
        stack.last().children += Node.End
    }

    /** Closes the tags above [depth]. Text only goes into the innermost frame, so that is the one to flush. */
    private fun closeTo(depth: Int) {
        while (stack.size > depth) {
            val frame = stack.removeLast().also(Frame::flush)
            stack.last().children += Node.Styled(checkNotNull(frame.style), frame.children)
        }
    }

    private fun fail(
        code: TextErrorCode,
        tag: Tag,
        reason: String,
    ): Nothing? = fail(code, tag.start, tag.length, reason)

    private fun fail(
        code: TextErrorCode,
        offset: Int,
        length: Int,
        reason: String,
    ): Nothing? {
        errors += TextError(code, offset, length, reason)
        return null
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{([A-Za-z][A-Za-z0-9_]*)}")
        const val RESET = "reset"
        const val COLOR = "color"
        const val GRADIENT = "gradient"
        const val RAINBOW = "rainbow"
    }
}
