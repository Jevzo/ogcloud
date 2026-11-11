package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.contracts.NodeErrors
import io.ogwars.cloud.text.RichText

/** The catalog text for a reason the gateway gave (D-0068, D-0132); an unknown reason is shown as it came. */
fun Messages.reason(reason: String): RichText =
    when (reason) {
        NodeErrors.NOT_PERMITTED -> text(Message.ERROR_NOT_PERMITTED)
        NodeErrors.PLAYER_ELSEWHERE -> text(Message.ERROR_PLAYER_ELSEWHERE)
        NodeErrors.UNKNOWN_GROUP -> text(Message.ERROR_UNKNOWN_GROUP)
        NodeErrors.UNKNOWN_PERMISSION_GROUP -> text(Message.ERROR_UNKNOWN_PERMISSION_GROUP)
        NodeErrors.NOT_OWN_SERVER -> text(Message.ERROR_NOT_OWN_SERVER)
        NodeErrors.UNAVAILABLE -> text(Message.ERROR_UNAVAILABLE)
        NodeErrors.INVALID, NodeErrors.NODE_TYPE -> text(Message.ERROR_INVALID)
        else -> RichText.plain(reason)
    }
