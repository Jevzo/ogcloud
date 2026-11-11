package io.ogwars.cloud.api.text

import io.ogwars.cloud.api.audit.NotAudited
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.text.Color
import io.ogwars.cloud.text.Decoration
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.Span
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.legacyColor
import io.ogwars.cloud.text.spans
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TextPreviewRequest(
    /** The text as it would be stored, in the tag format (D-0200). */
    val text: String,
    /** The stored text it is for, which decides its placeholders. */
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

data class TextErrorView(
    /** A character index into the text. */
    val offset: Int,
    val reason: String,
)

/** Either the errors that keep the text from being saved, or how 1.16+ clients and older ones see it. */
data class TextPreview(
    val errors: List<TextErrorView>,
    val modern: List<TextSpanView>,
    val legacy: List<TextSpanView>,
)

/** Previews in-game texts for the dashboard, which parses no tags itself (D-0202). */
@RestController
@RequestMapping("/api/v1/text")
class TextController {
    /**
     * How a text looks once saved, its placeholders filled with samples: as 1.16+ clients see it and as older clients
     * do, every colour the nearest named one (D-0204). It reads no data, so any caller may ask.
     */
    @AnyCaller
    @NotAudited
    @PostMapping("/preview")
    fun preview(
        @RequestBody request: TextPreviewRequest,
    ): TextPreview {
        val errors = request.field.validate(request.text).map { TextErrorView(it.offset, it.reason) }
        if (errors.isNotEmpty()) return TextPreview(errors, emptyList(), emptyList())
        val text = RichText.parseValid(request.text).fill(request.field.samples)
        return TextPreview(
            emptyList(),
            text.spans().map { it.toView(it.color) },
            text.spans(request.field.collapsesOnLegacy).map { it.toView(it.color?.let(::legacyColor)) },
        )
    }
}

private fun Span.toView(shown: Color?) =
    TextSpanView(
        text,
        shown?.let { "#%06x".format(it.rgb) },
        Decoration.BOLD in decorations,
        Decoration.ITALIC in decorations,
        Decoration.UNDERLINED in decorations,
        Decoration.STRIKETHROUGH in decorations,
        Decoration.OBFUSCATED in decorations,
    )
