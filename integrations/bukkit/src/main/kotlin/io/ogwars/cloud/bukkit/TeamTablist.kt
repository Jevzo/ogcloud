package io.ogwars.cloud.bukkit

import io.ogwars.cloud.node.core.format.Tablist
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.Placeholders
import io.ogwars.cloud.text.colorOf
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

/**
 * Tablist names by rank, as v1: every player gets their own scoreboard with one team per permission group, so the
 * tablist sorts by weight and shows each group's prefix and the colour its name format shows names in (D-0206). Runs
 * on the server thread.
 */
class TeamTablist(
    private val server: Server,
    private val line: BukkitLine,
    private val texts: StoredTexts,
    private val rankOf: (UUID) -> Rank?,
) {
    var enabled = false
        private set

    fun enable(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) server.onlinePlayers.forEach(::show) else clearAll()
    }

    /** A joining player sees everyone's teams, and everyone sees theirs. */
    fun show(player: Player) {
        if (!enabled) return
        val board = server.scoreboardManager.newScoreboard
        player.scoreboard = board
        server.onlinePlayers.forEach { place(board, it) }
        others(player).forEach { place(it.scoreboard, player) }
    }

    /** A player's rank changed: their entry moves to the new team on every board. */
    fun refresh(player: Player) {
        if (!enabled) return
        (others(player) + player).forEach { place(it.scoreboard, player) }
    }

    fun remove(player: Player) = others(player).forEach { leaveTeams(it.scoreboard, player.name) }

    private fun clearAll() = server.onlinePlayers.forEach { it.scoreboard = server.scoreboardManager.mainScoreboard }

    private fun place(
        board: Scoreboard,
        player: Player,
    ) {
        leaveTeams(board, player.name)
        val group = rankOf(player.uniqueId)?.group ?: return
        val team = board.getTeam(Tablist.teamName(group)) ?: board.registerNewTeam(Tablist.teamName(group))
        line.styleTeam(team, texts.parse(group.tabPrefix), texts.parse(group.nameFormat).colorOf(Placeholders.NAME))
        team.addEntry(player.name)
    }

    private fun leaveTeams(
        board: Scoreboard,
        entry: String,
    ) = board.getEntryTeam(entry)?.removeEntry(entry)

    private fun others(player: Player) = server.onlinePlayers.filter { it.uniqueId != player.uniqueId }
}
