package io.ogwars.cloud.domain.session

import java.util.UUID

/** Which online players a listing keeps: by a name prefix, ignoring case (D-0223), and by server (D-0232). */
data class OnlineFilter(
    val namePrefix: String? = null,
    /** A backend keeps the players on it; a proxy keeps those connected through it. */
    val serverId: UUID? = null,
) {
    fun keeps(session: PlayerSession): Boolean =
        (namePrefix == null || session.playerName.startsWith(namePrefix, ignoreCase = true)) &&
            (serverId == null || session.serverId == serverId || session.proxyId == serverId)

    companion object {
        val ALL = OnlineFilter()
    }
}
