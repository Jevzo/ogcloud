package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages

/**
 * The `&`-coded start of a chat line: prefix, name colour, name and suffix. The platform appends the player's message
 * as plain text in the head's last colour, so `&` typed in chat stays literal.
 */
class ChatFormat(
    private val messages: Messages,
) {
    fun head(
        rank: Rank?,
        playerName: String,
    ): String {
        val group = rank?.group
        // A blank colour or suffix would glue name and message together; the catalog's defaults fill in.
        val nameColor = group?.nameColor?.ifBlank { null } ?: messages.text(Message.CHAT_NAME_COLOR)
        val suffix = group?.chatSuffix?.ifBlank { null } ?: messages.text(Message.CHAT_SUFFIX)
        return group?.chatPrefix.orEmpty() + nameColor + playerName + suffix
    }
}
