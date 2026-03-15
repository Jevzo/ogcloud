package io.ogwars.cloud.paper.tablist

import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.CachedPermission
import io.ogwars.cloud.paper.permission.PermissionManager
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

class TablistTeamManager(
    private val permissionManager: PermissionManager,
    private val networkFeatureState: NetworkFeatureState,
) {
    fun setTablistForMe(player: Player) {
        if (!networkFeatureState.tablistEnabled) {
            return
        }

        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        player.scoreboard = scoreboard

        Bukkit.getOnlinePlayers().forEach { target ->
            addPlayerToScoreboard(scoreboard, target)
        }
    }

    fun setTablistForOthers(player: Player) {
        if (!networkFeatureState.tablistEnabled) {
            return
        }

        val cached = permissionManager.getCachedPlayer(player.uniqueId) ?: return

        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .filterNot { it.uniqueId == player.uniqueId }
            .forEach { viewer ->
                addEntry(viewer.scoreboard, player.name, cached)
            }
    }

    fun refreshPlayer(player: Player) {
        if (!networkFeatureState.tablistEnabled) {
            return
        }

        val cached = permissionManager.getCachedPlayer(player.uniqueId) ?: return

        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .map(Player::getScoreboard)
            .plus(sequenceOf(player.scoreboard))
            .forEach { scoreboard ->
                removeFromAllTeams(scoreboard, player.name)
                addEntry(scoreboard, player.name, cached)
            }
    }

    fun removePlayer(player: Player) {
        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .filterNot { it.uniqueId == player.uniqueId }
            .forEach { viewer ->
                removeFromAllTeams(viewer.scoreboard, player.name)
            }
    }

    fun clearAll() {
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()

        onlinePlayers.forEach { viewer ->
            val scoreboard = viewer.scoreboard

            onlinePlayers.forEach { target ->
                removeFromAllTeams(scoreboard, target.name)
            }
        }
    }

    private fun addPlayerToScoreboard(
        scoreboard: Scoreboard,
        player: Player,
    ) {
        val cached = permissionManager.getCachedPlayer(player.uniqueId) ?: return
        addEntry(scoreboard, player.name, cached)
    }

    private fun addEntry(
        scoreboard: Scoreboard,
        entryName: String,
        cached: CachedPermission,
    ) {
        getOrCreateTeam(scoreboard, cached).addEntry(entryName)
    }

    private fun getOrCreateTeam(
        scoreboard: Scoreboard,
        cached: CachedPermission,
    ): Team {
        val teamName = buildTeamName(cached)
        val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)

        team.prefix = buildTeamPrefix(cached)

        return team
    }

    private fun buildTeamName(cached: CachedPermission): String =
        "%04d%s".format(cached.weight, cached.groupId).take(MAX_TEAM_NAME_LENGTH)

    private fun buildTeamPrefix(cached: CachedPermission): String {
        val prefix = translateLegacy(cached.tabPrefix)
        val color = parseNameColor(cached.nameColor).toString()
        val maxPrefixLength = (MAX_PREFIX_LENGTH - color.length).coerceAtLeast(0)

        return prefix.take(maxPrefixLength) + color
    }

    private fun removeFromAllTeams(
        scoreboard: Scoreboard,
        entry: String,
    ) {
        scoreboard.teams.forEach { team ->
            if (team.hasEntry(entry)) {
                team.removeEntry(entry)
            }
        }
    }

    private fun translateLegacy(text: String): String =
        ChatColor.translateAlternateColorCodes('&', text.replace('\u00A7', '&'))

    private fun parseNameColor(nameColor: String): ChatColor {
        val code = nameColor.lastOrNull { it != '&' } ?: DEFAULT_COLOR_CODE
        return COLOR_MAP[code.lowercaseChar()] ?: ChatColor.GRAY
    }

    companion object {
        private const val DEFAULT_COLOR_CODE = '7'
        private const val MAX_TEAM_NAME_LENGTH = 16
        private const val MAX_PREFIX_LENGTH = 16

        private val COLOR_MAP =
            mapOf(
                '0' to ChatColor.BLACK,
                '1' to ChatColor.DARK_BLUE,
                '2' to ChatColor.DARK_GREEN,
                '3' to ChatColor.DARK_AQUA,
                '4' to ChatColor.DARK_RED,
                '5' to ChatColor.DARK_PURPLE,
                '6' to ChatColor.GOLD,
                '7' to ChatColor.GRAY,
                '8' to ChatColor.DARK_GRAY,
                '9' to ChatColor.BLUE,
                'a' to ChatColor.GREEN,
                'b' to ChatColor.AQUA,
                'c' to ChatColor.RED,
                'd' to ChatColor.LIGHT_PURPLE,
                'e' to ChatColor.YELLOW,
                'f' to ChatColor.WHITE,
            )
    }
}
