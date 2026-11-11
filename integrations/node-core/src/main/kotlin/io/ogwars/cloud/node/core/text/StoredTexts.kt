package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.RichText
import java.util.concurrent.ConcurrentHashMap

/**
 * Texts the network stores or sends, such as a group's prefix, the tablist header or a kick message, each parsed once.
 * They are checked when written (D-0205); one that still fails, as from an older release, is shown as written and
 * logged when first parsed.
 */
class StoredTexts {
    private val log = System.getLogger(StoredTexts::class.java.name)
    private val parsed = ConcurrentHashMap<String, RichText>()

    fun parse(source: String): RichText {
        parsed[source]?.let { return it }
        // Chat and the tablist read these on many threads; dropping all at the bound is cheaper than tracking use.
        if (parsed.size >= CAPACITY) parsed.clear()
        return parsed.computeIfAbsent(source) {
            RichText.stored(it) { errors ->
                log.log(
                    System.Logger.Level.WARNING,
                    "A stored text is shown as written, since it is not valid: \"{0}\" {1}",
                    it.take(LOGGED_CHARACTERS),
                    errors,
                )
            }
        }
    }

    private companion object {
        // The tablist, NPC lines and every group's texts fit, so each is parsed and logged once.
        const val CAPACITY = 256
        const val LOGGED_CHARACTERS = 100
    }
}
