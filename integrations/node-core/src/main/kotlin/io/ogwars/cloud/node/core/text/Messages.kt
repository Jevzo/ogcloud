package io.ogwars.cloud.node.core.text

import java.util.Locale
import java.util.ResourceBundle

/**
 * In-game texts from the message catalog (D-0050), `&`-coded. Placeholders are filled in one pass, so a value that
 * looks like a placeholder, e.g. in a player's chat, stays as it is.
 */
class Messages(
    private val catalog: Map<String, String>,
) {
    init {
        val missing = Message.entries.map { it.key }.filterNot(catalog::containsKey)
        require(missing.isEmpty()) { "The message catalog lacks $missing" }
    }

    fun text(
        message: Message,
        vararg values: Pair<String, Any?>,
    ): String {
        val given = values.toMap()
        require(given.keys == message.placeholders) {
            "${message.key} takes ${message.placeholders}, got ${given.keys}"
        }
        return PLACEHOLDER.replace(catalog.getValue(message.key)) { match ->
            given[match.groupValues[1]]?.toString() ?: match.value
        }
    }

    companion object {
        /** `{name}`, as the catalog writes placeholders. */
        val PLACEHOLDER = Regex("\\{([a-z_]+)}")
        private const val BUNDLE = "io.ogwars.cloud.node.core.messages"

        /** The catalog for [locale]; English is the only one that ships for now. */
        @JvmStatic
        fun load(locale: Locale = Locale.ENGLISH): Messages {
            val bundle = ResourceBundle.getBundle(BUNDLE, locale)
            return Messages(bundle.keySet().associateWith(bundle::getString))
        }
    }
}
