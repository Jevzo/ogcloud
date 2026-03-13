package io.ogwars.cloud.paper.tablist

import io.ogwars.cloud.paper.compat.BukkitCompatibility
import io.ogwars.cloud.paper.compat.LegacyTextSupport
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.CachedPermission
import io.ogwars.cloud.paper.permission.PermissionManager
import org.bukkit.Bukkit
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
        BukkitCompatibility.addTeamEntry(getOrCreateTeam(scoreboard, cached), entryName)
    }

    private fun getOrCreateTeam(
        scoreboard: Scoreboard,
        cached: CachedPermission,
    ): Team {
        val teamName = buildTeamName(cached)
        val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)

        BukkitCompatibility.setTeamPrefix(team, LegacyTextSupport.buildTeamPrefix(cached.tabPrefix, cached.nameColor))

        return team
    }

    private fun buildTeamName(cached: CachedPermission): String =
        "%04d%s".format(cached.weight, cached.groupId).take(MAX_TEAM_NAME_LENGTH)

    private fun removeFromAllTeams(
        scoreboard: Scoreboard,
        entry: String,
    ) {
        for (team in scoreboard.teams) {
            if (BukkitCompatibility.teamHasEntry(team, entry)) {
                BukkitCompatibility.removeTeamEntry(team, entry)
            }
        }
    }

    companion object {
        private const val MAX_TEAM_NAME_LENGTH = 16
    }
}
