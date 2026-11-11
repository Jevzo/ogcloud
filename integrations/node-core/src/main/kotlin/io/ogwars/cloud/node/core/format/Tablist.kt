package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.node.core.text.storedText
import io.ogwars.cloud.text.Placeholders
import io.ogwars.cloud.text.RichText

/** What the network's tablist header and footer may show, under v1's placeholder names. */
data class TablistValues(
    val server: String,
    val group: String,
    val proxy: String,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val ping: Long,
    val permissionGroup: String,
)

object Tablist {
    /** The tablist header or footer with its placeholders filled in (D-0200). */
    @JvmStatic
    fun fill(
        template: String,
        values: TablistValues,
    ): RichText =
        storedText(template).fill(
            Placeholders.SERVER to values.server,
            Placeholders.GROUP to values.group,
            Placeholders.PROXY to values.proxy,
            Placeholders.ONLINE_PLAYERS to values.onlinePlayers.toString(),
            Placeholders.MAX_PLAYERS to values.maxPlayers.toString(),
            Placeholders.PING to values.ping.toString(),
            Placeholders.PERMISSION_GROUP to values.permissionGroup,
        )

    /**
     * The scoreboard team of a permission group. Clients sort teams by name, so the name starts with the weight
     * reversed (heaviest first, as v1); 1.8 clients allow 16 characters.
     */
    @JvmStatic
    fun teamName(group: PermissionGroup): String {
        val order = (MAX_WEIGHT - group.weight.coerceIn(0, MAX_WEIGHT)).toString().padStart(WEIGHT_DIGITS, '0')
        val id = group.permissionGroupId
        // Long ids keep a hash, so two groups sharing a prefix still get their own team.
        val suffix = if (id.length <= ID_ROOM) id else id.take(ID_ROOM - HASH_DIGITS) + hash(id)
        return order + suffix
    }

    private fun hash(id: String) = Integer.toHexString(id.hashCode()).padStart(8, '0').takeLast(HASH_DIGITS)

    private const val MAX_WEIGHT = 9999
    private const val WEIGHT_DIGITS = 4
    private const val ID_ROOM = 12
    private const val HASH_DIGITS = 5
}
