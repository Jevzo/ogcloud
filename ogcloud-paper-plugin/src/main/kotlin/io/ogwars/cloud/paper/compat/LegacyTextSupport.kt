package io.ogwars.cloud.paper.compat

object LegacyTextSupport {
    private const val SECTION_SIGN = '\u00A7'
    private const val ALT_COLOR_CHAR = '&'
    private const val MAX_TEAM_PREFIX_LENGTH = 16
    private const val EMPTY_PREFIX_BUDGET = 0
    private val LEGACY_CODES = "0123456789abcdefklmnor"

    fun colorize(text: String): String {
        if (text.isEmpty()) {
            return text
        }

        val normalized = text.replace(SECTION_SIGN, ALT_COLOR_CHAR)
        val builder = StringBuilder(normalized.length)
        var index = 0

        while (index < normalized.length) {
            val current = normalized[index]

            if (current == ALT_COLOR_CHAR && index + 1 < normalized.length) {
                val code = normalized[index + 1].lowercaseChar()
                if (code in LEGACY_CODES) {
                    builder.append(SECTION_SIGN).append(code)
                    index += 2
                    continue
                }
            }

            builder.append(current)
            index++
        }

        return builder.toString()
    }

    fun escapePercentSigns(text: String): String = text.replace("%", "%%")

    fun buildTeamPrefix(
        tabPrefix: String,
        nameColor: String,
    ): String {
        val translatedPrefix = colorize(tabPrefix)
        val translatedNameColor = colorize(nameColor)
        val prefixBudget = (MAX_TEAM_PREFIX_LENGTH - translatedNameColor.length).coerceAtLeast(EMPTY_PREFIX_BUDGET)

        return truncateLegacyText(truncateLegacyText(translatedPrefix, prefixBudget) + translatedNameColor)
    }

    private fun truncateLegacyText(
        text: String,
        maxLength: Int = MAX_TEAM_PREFIX_LENGTH,
    ): String {
        if (text.length <= maxLength) {
            return text
        }

        val truncated = text.substring(0, maxLength)
        return if (truncated.endsWith(SECTION_SIGN)) {
            truncated.dropLast(1)
        } else {
            truncated
        }
    }
}
