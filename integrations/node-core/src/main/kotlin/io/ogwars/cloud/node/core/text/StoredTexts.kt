package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.RichText

/**
 * Texts the network stores or sends, such as a group's prefix, the tablist header or a kick message, each parsed once.
 * They are checked when written (D-0205); one that still fails, as from an older release, is shown as written and
 * logged once while it stays in use.
 */
class StoredTexts {
    private val log = System.getLogger(StoredTexts::class.java.name)
    private val parsed =
        object : LinkedHashMap<String, RichText>(CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RichText>) = size > CAPACITY
        }

    fun parse(source: String): RichText =
        synchronized(parsed) {
            parsed.getOrPut(source) {
                RichText.stored(source) { errors ->
                    log.log(
                        System.Logger.Level.WARNING,
                        "A stored text is shown as written, since it is not valid: \"{0}\" {1}",
                        source.take(LOGGED_CHARACTERS),
                        errors,
                    )
                }
            }
        }

    private companion object {
        // The tablist, NPC lines and every group's texts fit, so each is parsed and logged once.
        const val CAPACITY = 256
        const val LOAD_FACTOR = 0.75f
        const val LOGGED_CHARACTERS = 100
    }
}
