package io.ogwars.cloud.text

/**
 * The text as a chat component in Minecraft's JSON, for 1.16 and newer clients' server list (D-0202): the load
 * balancer embeds it in its status answer. Named colours keep their names; others are written as `#rrggbb`.
 */
fun RichText.toJson(): String {
    val extra =
        runs().joinToString(",") { run ->
            buildString {
                append("{\"text\":").append(quote(run.text))
                run.color?.let { append(",\"color\":").append(quote(colorName(it))) }
                run.decorations.forEach { append(",\"").append(it.tag).append("\":true") }
                append('}')
            }
        }
    return if (extra.isEmpty()) "{\"text\":\"\"}" else "{\"text\":\"\",\"extra\":[$extra]}"
}

private fun colorName(color: Color): String =
    when (color) {
        is Color.Named -> color.name
        is Color.Hex -> "#%06x".format(color.rgb)
    }

private fun quote(text: String): String =
    buildString {
        append('"')
        for (char in text) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }
