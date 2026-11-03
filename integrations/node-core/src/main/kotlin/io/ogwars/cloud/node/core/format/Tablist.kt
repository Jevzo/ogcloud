package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.contracts.node.v1.PermissionGroup

/** What the network's tablist header and footer may show, with v1's placeholder names. */
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
    private val PLACEHOLDER = Regex("%([A-Za-z]+)%")

    /** Fills [template] in one pass; unknown placeholders stay. */
    @JvmStatic
    fun fill(
        template: String,
        values: TablistValues,
    ): String {
        val byName =
            mapOf(
                "server" to values.server,
                "group" to values.group,
                "proxy" to values.proxy,
                "onlinePlayers" to values.onlinePlayers.toString(),
                "maxPlayers" to values.maxPlayers.toString(),
                "ping" to values.ping.toString(),
                "permissionGroup" to values.permissionGroup,
            )
        return PLACEHOLDER.replace(template) { byName[it.groupValues[1]] ?: it.value }
    }

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
