package io.ogwars.cloud.api.text

import io.ogwars.cloud.api.audit.NotAudited
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.text.Decoration
import io.ogwars.cloud.text.ParseResult
import io.ogwars.cloud.text.Span
import io.ogwars.cloud.text.TextError
import io.ogwars.cloud.text.TextErrorCode
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.legacySpans
import io.ogwars.cloud.text.spans
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TextPreviewRequest(
    /** The text as it would be stored, in the tag format (D-0200). */
    val text: String,
    /** The text it is for, which decides its placeholders and how older clients see it. */
    val field: TextField,
)

/** One stretch of the preview with one colour (`#rrggbb`, or null for the client's default) and its decorations. */
data class TextSpanView(
    val text: String,
    val color: String?,
    val bold: Boolean,
    val italic: Boolean,
    val underlined: Boolean,
    val strikethrough: Boolean,
    val obfuscated: Boolean,
)

/** What is wrong where in a text (D-0205); saves answer the same in their problem's `textErrors`. */
data class TextErrorView(
    val code: TextErrorCode,
    /** A UTF-16 index into the text, as JavaScript counts it. */
    val offset: Int,
    /** The UTF-16 units it covers; 0 where something is missing. */
    val length: Int,
    /** In English, for logs; the dashboard says it in its own words from [code]. */
    val reason: String,
) {
    companion object {
        fun of(error: TextError) = TextErrorView(error.code, error.offset, error.length, error.reason)
    }
}

/**
 * How a valid text looks: to 1.16+ clients, and to older ones in the nearest named colours (D-0204), with gradients
 * collapsed and cut where 1.8 caps the length. Texts every client reads as legacy text show that in both.
 */
data class TextRendering(
    val modern: List<TextSpanView>,
    val legacy: List<TextSpanView>,
)

/** The errors that keep the text from being saved or, when it has none, how it looks. */
data class TextPreview(
    val errors: List<TextErrorView>,
    val rendering: TextRendering?,
    /** The placeholders the field offers, without braces, so editors can insert them (D-0200). */
    val placeholders: List<String>,
) {
    init {
        require(errors.isEmpty() == (rendering != null)) { "A preview has either errors or a rendering" }
    }
}

/** Previews in-game texts for the dashboard, which parses no tags itself (D-0202). */
@RestController
@RequestMapping("/api/v1/text")
class TextController {
    /**
     * How a text looks once saved, its placeholders filled with samples. It reads and changes nothing, so any caller
     * may ask and it is not audited.
     */
    @AnyCaller
    @NotAudited
    @PostMapping("/preview")
    fun preview(
        @RequestBody request: TextPreviewRequest,
    ): TextPreview {
        val field = request.field
        val placeholders = field.samples.keys.toList()
        return when (val result = field.parse(request.text)) {
            is ParseResult.Invalid -> {
                TextPreview(result.errors.map(TextErrorView::of), null, placeholders)
            }

            is ParseResult.Valid -> {
                val text = result.text.fill(field.samples)
                val legacy = text.legacySpans(field.collapsesOnLegacy, field.legacyMaxLength).map { it.toView() }
                val modern = if (field.legacyOnly) legacy else text.spans().map { it.toView() }
                TextPreview(emptyList(), TextRendering(modern, legacy), placeholders)
            }
        }
    }
}

private fun Span.toView() =
    TextSpanView(
        text,
        color?.hex,
        Decoration.BOLD in decorations,
        Decoration.ITALIC in decorations,
        Decoration.UNDERLINED in decorations,
        Decoration.STRIKETHROUGH in decorations,
        Decoration.OBFUSCATED in decorations,
    )
