package io.ogwars.cloud.text

/** Every text the API stores or sends in the tag format, with the placeholders it offers and sample values for previews. */
enum class TextField(
    /** The placeholders this text may use, each with the sample value a preview fills in. */
    val samples: Map<String, String> = emptyMap(),
    /** Placeholders a non-empty text must use. */
    val required: Set<String> = emptySet(),
    /** 1.8 caps its length, so its gradients and rainbows take their first colour there (D-0204). */
    val collapsesOnLegacy: Boolean = false,
    /** The legacy characters 1.8 shows of it, codes included. */
    val legacyMaxLength: Int = Int.MAX_VALUE,
    /** Every client sees it as legacy text, as the server list's version name, a plain string. */
    val legacyOnly: Boolean = false,
) {
    CHAT_PREFIX,
    CHAT_SUFFIX,

    /** Cut before the name colour's code, which a 1.8 team prefix keeps (see [legacyTeamPrefix]). */
    TAB_PREFIX(
        collapsesOnLegacy = true,
        legacyMaxLength = LEGACY_TEAM_PREFIX_LENGTH - LEGACY_CODE_LENGTH,
    ),
    NAME_FORMAT(mapOf(Placeholders.NAME to "Steve"), required = setOf(Placeholders.NAME)),
    MOTD,
    MAINTENANCE_MOTD,
    VERSION_NAME(legacyOnly = true),
    MAINTENANCE_VERSION_NAME(legacyOnly = true),
    TABLIST_HEADER(TABLIST_SAMPLES),
    TABLIST_FOOTER(TABLIST_SAMPLES),
    MAINTENANCE_KICK_MESSAGE,

    /** What players see when an API transfer kicks them (D-0206). */
    KICK_MESSAGE,
    NPC_TITLE(NPC_SAMPLES),
    NPC_SUBTITLE(NPC_SAMPLES),
    ;

    init {
        require(samples.keys.containsAll(required)) { "$name must offer the placeholders it requires" }
    }

    /**
     * [source] parsed, or what keeps it from being stored in this field: tag errors first, placeholder errors once
     * the tags parse. An empty text is always allowed.
     */
    fun parse(source: String): ParseResult {
        if (source.isEmpty()) return ParseResult.Valid(RichText.plain(source))
        if (source.length > MAX_LENGTH) return ParseResult.Invalid(listOf(tooLong(source)))
        val signs =
            source.indices.filter { source[it] == SECTION_SIGN }.map {
                TextError(TextErrorCode.SECTION_SIGN, it, 1, "'$SECTION_SIGN' is a legacy code; write tags instead")
            }
        return when (val result = RichText.parse(source)) {
            is ParseResult.Invalid -> {
                invalid(result.errors + signs)
            }

            is ParseResult.Valid -> {
                val errors = signs + placeholderErrors(result.text, source)
                if (errors.isEmpty()) result else invalid(errors)
            }
        }
    }

    /** What keeps [source] from being stored in this field; empty when it may be. */
    fun validate(source: String): List<TextError> = (parse(source) as? ParseResult.Invalid)?.errors.orEmpty()

    /** Refuses [source] with an IllegalArgumentException naming [label] and each error. */
    fun require(
        label: String,
        source: String,
    ) {
        val errors = validate(source)
        require(errors.isEmpty()) { "$label: ${errors.describe()}" }
    }

    private fun invalid(errors: List<TextError>) = ParseResult.Invalid(errors.sortedBy(TextError::offset))

    private fun placeholderErrors(
        text: RichText,
        source: String,
    ): List<TextError> {
        val used = text.placeholderNodes()
        val offered = if (samples.isEmpty()) "none" else samples.keys.joinToString { "{$it}" }
        val unknown =
            used.filter { it.name !in samples }.map {
                TextError(
                    TextErrorCode.UNKNOWN_PLACEHOLDER,
                    it.offset,
                    it.name.length + BRACES,
                    "{${it.name}} is no placeholder here; this text offers $offered",
                )
            }
        val missing =
            (required - used.mapTo(mutableSetOf()) { it.name }).map {
                TextError(TextErrorCode.MISSING_PLACEHOLDER, source.length, 0, "this text must show {$it}")
            }
        return unknown + missing
    }

    companion object {
        /** The most characters a stored text or a preview may have (D-0205). */
        const val MAX_LENGTH = 2048

        private const val BRACES = 2
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

/** The placeholder names the stored texts are shown with; node-core and the npc module fill them, so they live here. */
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

    /** The live player count of an NPC's target group. */
    const val PLAYERS = "players"

    /** The live server count of an NPC's target group. */
    const val SERVERS = "servers"
}
