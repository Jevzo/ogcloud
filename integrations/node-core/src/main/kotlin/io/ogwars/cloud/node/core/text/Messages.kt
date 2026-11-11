package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.RichText
import java.util.Locale
import java.util.ResourceBundle

/**
 * In-game texts from the message catalog (D-0050) in the tag format (D-0200), parsed once. A value is shown as plain
 * text, so a player's chat or a server id never adds formatting; a value that is itself a [RichText], such as a
 * group's prefix or another catalog text, keeps its own.
 */
class Messages(
    catalog: Map<String, String>,
) {
    private val texts: Map<Message, RichText>

    init {
        val missing = Message.entries.map { it.key }.filterNot(catalog::containsKey)
        require(missing.isEmpty()) { "The message catalog lacks $missing" }
        texts =
            Message.entries.associateWith { message ->
                val text = RichText.parseValid(catalog.getValue(message.key))
                require(text.placeholders() == message.placeholders) {
                    "${message.key} uses ${text.placeholders()}, but takes ${message.placeholders}"
                }
                text
            }
    }

    fun text(
        message: Message,
        vararg values: Pair<String, Any?>,
    ): RichText {
        val given = values.toMap()
        require(given.keys == message.placeholders) {
            "${message.key} takes ${message.placeholders}, got ${given.keys}"
        }
        val filled =
            given.filterValues { it != null }.mapValues { (_, value) -> value as? RichText ?: RichText.plain("$value") }
        return texts.getValue(message).insert(filled)
    }

    companion object {
        private const val BUNDLE = "io.ogwars.cloud.node.core.messages"

        /** The catalog for [locale]; English is the only one that ships for now. */
        @JvmStatic
        fun load(locale: Locale = Locale.ENGLISH): Messages {
            val bundle = ResourceBundle.getBundle(BUNDLE, locale)
            return Messages(bundle.keySet().associateWith(bundle::getString))
        }
    }
}
