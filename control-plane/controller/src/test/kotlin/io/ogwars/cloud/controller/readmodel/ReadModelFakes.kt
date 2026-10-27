package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.Revisioned
import java.time.Instant
import java.util.UUID

class RecordingBroadcasts : NodeBroadcasts {
    val sent = mutableListOf<NodeBroadcast>()

    override fun send(
        subjectId: String,
        update: NodeBroadcast.Builder.() -> Unit,
    ) {
        sent +=
            NodeBroadcast
                .newBuilder()
                .setSubjectId(subjectId)
                .apply(update)
                .build()
    }
}

fun permissionGroup(
    id: String,
    isDefault: Boolean = false,
    permissions: Map<String, String> = emptyMap(),
) = PermissionGroup(id, id.replaceFirstChar(Char::uppercase), weight = 0, permissions, RankDisplay(), isDefault)

class InMemoryPermissionGroupStore(
    vararg groups: PermissionGroup,
) : PermissionGroupStore {
    private val groups = groups.associate { it.id to Revisioned(it, 1L) }.toMutableMap()

    override fun findAll() = groups.values.toList()

    override fun find(id: String) = groups[id]

    override fun findDefault() = groups.values.single { it.value.isDefault }

    override fun save(group: PermissionGroup) {
        groups[group.id] = Revisioned(group, (groups[group.id]?.revision ?: 0) + 1)
    }

    override fun delete(id: String) {
        groups.remove(id)
    }
}

class InMemoryNpcStore : NpcStore {
    private val npcs = mutableMapOf<UUID, Npc>()

    override fun findByGroup(groupId: GroupId) = npcs.values.filter { it.groupId == groupId }

    override fun find(id: UUID) = npcs[id]

    override fun save(npc: Npc) {
        npcs[npc.id] = npc
    }

    override fun delete(id: UUID) {
        npcs.remove(id)
    }
}

class InMemoryPlayerStore : PlayerStore {
    private val players = mutableMapOf<UUID, Player>()

    override fun find(uuid: UUID) = players[uuid]

    override fun findByName(name: String) = players.values.find { it.name.equals(name, ignoreCase = true) }

    override fun recordSeen(
        uuid: UUID,
        name: String,
        at: Instant,
        defaultRank: PlayerRank,
    ): Player {
        val player = players[uuid]?.copy(name = name, lastSeenAt = at) ?: Player(uuid, name, defaultRank, 0, at, at)
        players[uuid] = player
        return player
    }

    override fun updateRank(rank: PlayerRank): Long {
        val player = players.getValue(rank.playerUuid)
        val updated = player.copy(rank = rank, rankVersion = player.rankVersion + 1)
        players[rank.playerUuid] = updated
        return updated.rankVersion
    }

    override fun withExpiredRanks(
        now: Instant,
        limit: Int,
    ) = players.values.filter { it.rank.isExpired(now) }.take(limit)
}
