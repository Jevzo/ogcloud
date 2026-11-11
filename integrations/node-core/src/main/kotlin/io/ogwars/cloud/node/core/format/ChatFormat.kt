package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.storedText
import io.ogwars.cloud.text.Placeholders
import io.ogwars.cloud.text.RichText

/**
 * The start of a chat line: prefix, the name in its format (D-0203) and suffix. The platform appends the player's
 * message as plain text in the head's end colour, so tags typed in chat stay literal.
 */
class ChatFormat(
    private val messages: Messages,
) {
    fun head(
        rank: Rank?,
        playerName: String,
    ): RichText {
        val group = rank?.group
        // A blank name format or suffix would lose the name's colour or glue name and message; the catalog fills in.
        val name =
            group?.nameFormat?.ifBlank { null }?.let { storedText(it).fill(Placeholders.NAME to playerName) }
                ?: messages.text(Message.CHAT_NAME_FORMAT, Placeholders.NAME to playerName)
        val suffix = group?.chatSuffix?.ifBlank { null }?.let(::storedText) ?: messages.text(Message.CHAT_SUFFIX)
        return storedText(group?.chatPrefix.orEmpty()) + name + suffix
    }
}
