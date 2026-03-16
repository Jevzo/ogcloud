package io.ogwars.cloud.paper.message

object PaperMessages {
    object Chat {
        const val FORMAT = "%prefix%%name_color%%player_name%%suffix%%message%"
        const val DEFAULT_NAME_COLOR = "&7"
        const val DEFAULT_SUFFIX = ": &f"
    }

    fun format(
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ): String {
        if (placeholders.isEmpty()) {
            return template
        }

        var formatted = template
        placeholders.forEach { (name, value) ->
            formatted = formatted.replace("%$name%", value?.toString().orEmpty())
        }
        return formatted
    }
}
