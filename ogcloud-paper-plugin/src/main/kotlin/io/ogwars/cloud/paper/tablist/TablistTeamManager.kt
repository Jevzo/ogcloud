package io.ogwars.cloud.paper.tablist

import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.CachedPermission
import io.ogwars.cloud.paper.permission.PermissionManager
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

class TablistTeamManager(
    private val permissionManager: PermissionManager,
    private val networkFeatureState: NetworkFeatureState,
) {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun setTablistForMe(player: Player) {
        if (!networkFeatureState.tablistEnabled) {
            return
        }

        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        player.scoreboard = scoreboard

        Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
            addPlayerToScoreboard(scoreboard, onlinePlayer)
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
            .forEach { onlinePlayer -> addEntry(onlinePlayer.scoreboard, player.name, cached) }
    }

    fun refreshPlayer(player: Player) {
        if (!networkFeatureState.tablistEnabled) {
            return
        }

        val cached = permissionManager.getCachedPlayer(player.uniqueId) ?: return
        updatePlayerEntry(player, player.name, cached)
    }

    fun removePlayer(player: Player) {
        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .filterNot { it.uniqueId == player.uniqueId }
            .forEach { onlinePlayer -> removeFromAllTeams(onlinePlayer.scoreboard, player.name) }
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

    private fun updatePlayerEntry(
        player: Player,
        entryName: String,
        cached: CachedPermission,
    ) {
        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .filterNot { it.uniqueId == player.uniqueId }
            .map(Player::getScoreboard)
            .plus(sequenceOf(player.scoreboard))
            .forEach { scoreboard ->
                removeFromAllTeams(scoreboard, entryName)
                addEntry(scoreboard, entryName, cached)
            }
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

        team.prefix(deserializeLegacy(cached.tabPrefix))
        team.color(parseNameColor(cached.nameColor))

        return team
    }

    private fun buildTeamName(cached: CachedPermission): String =
        "%04d%s".format(cached.weight, cached.groupId).take(MAX_TEAM_NAME_LENGTH)

    private fun removeFromAllTeams(
        scoreboard: Scoreboard,
        entry: String,
    ) {
        for (team in scoreboard.teams) {
            if (team.hasEntry(entry)) {
                team.removeEntry(entry)
            }
        }
    }

    private fun deserializeLegacy(text: String) = legacySerializer.deserialize(text.replace('\u00A7', '&'))

    private fun parseNameColor(nameColor: String): NamedTextColor {
        val code = nameColor.lastOrNull { it != '&' } ?: DEFAULT_COLOR_CODE
        return COLOR_MAP[code.lowercaseChar()] ?: NamedTextColor.GRAY
    }

    companion object {
        private const val DEFAULT_COLOR_CODE = '7'
        private const val MAX_TEAM_NAME_LENGTH = 16

        private val COLOR_MAP =
            mapOf(
                '0' to NamedTextColor.BLACK,
                '1' to NamedTextColor.DARK_BLUE,
                '2' to NamedTextColor.DARK_GREEN,
                '3' to NamedTextColor.DARK_AQUA,
                '4' to NamedTextColor.DARK_RED,
                '5' to NamedTextColor.DARK_PURPLE,
                '6' to NamedTextColor.GOLD,
                '7' to NamedTextColor.GRAY,
                '8' to NamedTextColor.DARK_GRAY,
                '9' to NamedTextColor.BLUE,
                'a' to NamedTextColor.GREEN,
                'b' to NamedTextColor.AQUA,
                'c' to NamedTextColor.RED,
                'd' to NamedTextColor.LIGHT_PURPLE,
                'e' to NamedTextColor.YELLOW,
                'f' to NamedTextColor.WHITE,
            )
    }
}
