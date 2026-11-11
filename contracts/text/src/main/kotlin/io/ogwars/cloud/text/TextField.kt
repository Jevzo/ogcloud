package io.ogwars.cloud.text

/**
 * Every stored text the API accepts in the tag format, with the placeholders it is shown with and sample values for
 * previews. node-core and the npc module fill these names, so they come from here.
 */
enum class TextField(
    /** Placeholder name to the sample value a preview fills in. */
    val samples: Map<String, String> = emptyMap(),
    /** Placeholders the text must use. */
    val required: Set<String> = emptySet(),
    /** 1.8 caps its length, so its gradients take their first colour there (D-0204). */
    val collapsesOnLegacy: Boolean = false,
) {
    CHAT_PREFIX,
    CHAT_SUFFIX,
    TAB_PREFIX(collapsesOnLegacy = true),
    NAME_FORMAT(mapOf(Placeholders.NAME to "Steve"), required = setOf(Placeholders.NAME)),
    MOTD,
    MAINTENANCE_MOTD,
    VERSION_NAME,
    MAINTENANCE_VERSION_NAME,
    TABLIST_HEADER(TABLIST_SAMPLES),
    TABLIST_FOOTER(TABLIST_SAMPLES),
    MAINTENANCE_KICK_MESSAGE,
    NPC_TITLE(NPC_SAMPLES),
    NPC_SUBTITLE(NPC_SAMPLES),
    ;

    val placeholders: Set<String> get() = samples.keys

    /** What keeps [source] from being stored in this field; empty when it may be. An empty text is always allowed. */
    fun validate(source: String): List<TextError> {
        if (source.isEmpty()) return emptyList()
        return when (val result = RichText.parse(source)) {
            is ParseResult.Invalid -> result.errors
            is ParseResult.Valid -> placeholderErrors(result.text)
        }
    }

    private fun placeholderErrors(text: RichText): List<TextError> {
        val used = text.placeholderNodes()
        val unknown =
            used.filter { it.name !in placeholders }.map {
                val offered = if (placeholders.isEmpty()) "none" else placeholders.joinToString { name -> "{$name}" }
                TextError(it.offset, "{${it.name}} is no placeholder here; this text offers $offered")
            }
        val missing = (required - used.map { it.name }.toSet()).map { TextError(0, "this text must show {$it}") }
        return unknown + missing
    }
}

private val TABLIST_SAMPLES =
    mapOf(
        Placeholders.SERVER to "lobby-1",
        Placeholders.GROUP to "lobby",
        Placeholders.PROXY to "proxy-1",
        Placeholders.ONLINE_PLAYERS to "42",
        Placeholders.MAX_PLAYERS to "500",
        Placeholders.PING to "23",
        Placeholders.PERMISSION_GROUP to "VIP",
    )

private val NPC_SAMPLES = mapOf(Placeholders.PLAYERS to "42", Placeholders.SERVERS to "3")

/** The placeholder names the stored texts are shown with. */
object Placeholders {
    /** A permission group's name format: the player's name. */
    const val NAME = "name"
    const val SERVER = "server"
    const val GROUP = "group"
    const val PROXY = "proxy"
    const val ONLINE_PLAYERS = "onlinePlayers"
    const val MAX_PLAYERS = "maxPlayers"
    const val PING = "ping"
    const val PERMISSION_GROUP = "permissionGroup"

    /** An NPC's live counts of its target group. */
    const val PLAYERS = "players"
    const val SERVERS = "servers"
}
