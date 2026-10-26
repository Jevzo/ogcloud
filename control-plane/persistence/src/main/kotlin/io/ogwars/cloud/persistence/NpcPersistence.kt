package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.npc.NpcSkin
import io.ogwars.cloud.domain.store.NpcStore
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.util.UUID

@Repository
class JdbcNpcStore(
    private val jdbc: JdbcClient,
    private val json: JsonCodec,
    private val clock: Clock,
) : NpcStore {
    private val npcMapper =
        RowMapper { rs, _ ->
            Npc(
                id = rs.uuid("id"),
                groupId = GroupId(rs.getString("group_id")),
                location =
                    Location(
                        world = rs.getString("world"),
                        x = rs.getDouble("x"),
                        y = rs.getDouble("y"),
                        z = rs.getDouble("z"),
                        yaw = rs.getFloat("yaw"),
                        pitch = rs.getFloat("pitch"),
                    ),
                title = rs.getString("title"),
                subtitle = rs.getString("subtitle"),
                skin = rs.getString("skin_texture")?.let { NpcSkin(it, rs.getString("skin_signature")) },
                lookAtPlayers = rs.getBoolean("look_at_players"),
                leftClick = rs.getString("left_click")?.let(::readAction),
                rightClick = rs.getString("right_click")?.let(::readAction),
            )
        }

    override fun findByGroup(groupId: GroupId): List<Npc> =
        jdbc
            .sql("select * from npcs where group_id = :group order by created_at")
            .param("group", groupId.value)
            .query(npcMapper)
            .list()

    override fun find(id: UUID): Npc? =
        jdbc
            .sql("select * from npcs where id = :id")
            .param("id", id)
            .query(npcMapper)
            .optional()
            .orElse(null)

    override fun save(npc: Npc) {
        val now = clock.instant().toTimestamp()
        jdbc
            .sql(
                """
                insert into npcs (id, group_id, world, x, y, z, yaw, pitch, title, subtitle, skin_texture,
                    skin_signature, look_at_players, left_click, right_click, created_at, updated_at)
                values (:id, :group, :world, :x, :y, :z, :yaw, :pitch, :title, :subtitle, :skinTexture,
                    :skinSignature, :lookAt, cast(:left as jsonb), cast(:right as jsonb), :now, :now)
                on conflict (id) do update set group_id = excluded.group_id, world = excluded.world,
                    x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch,
                    title = excluded.title, subtitle = excluded.subtitle, skin_texture = excluded.skin_texture,
                    skin_signature = excluded.skin_signature, look_at_players = excluded.look_at_players,
                    left_click = excluded.left_click, right_click = excluded.right_click,
                    updated_at = excluded.updated_at, version = npcs.version + 1
                """.trimIndent(),
            ).param("id", npc.id)
            .param("group", npc.groupId.value)
            .param("world", npc.location.world)
            .param("x", npc.location.x)
            .param("y", npc.location.y)
            .param("z", npc.location.z)
            .param("yaw", npc.location.yaw)
            .param("pitch", npc.location.pitch)
            .param("title", npc.title)
            .param("subtitle", npc.subtitle)
            .param("skinTexture", npc.skin?.texture)
            .param("skinSignature", npc.skin?.signature)
            .param("lookAt", npc.lookAtPlayers)
            .param("left", npc.leftClick?.let(::writeAction))
            .param("right", npc.rightClick?.let(::writeAction))
            .param("now", now)
            .update()
    }

    override fun delete(id: UUID) {
        jdbc.sql("delete from npcs where id = :id").param("id", id).update()
    }

    private fun writeAction(action: NpcAction): String =
        when (action) {
            is NpcAction.Transfer ->
                json.write(
                    mapOf(TRANSFER to listOfNotNull("groupId" to action.groupId.value, action.routingStrategy?.let { "routingStrategy" to it.name }).toMap()),
                )
        }

    private fun readAction(text: String): NpcAction {
        val transfer = json.readTree(text).get(TRANSFER) ?: throw IllegalStateException("Unknown NPC action: $text")
        return NpcAction.Transfer(
            groupId = GroupId(transfer.get("groupId").asString()),
            routingStrategy = transfer.get("routingStrategy")?.asString()?.let(RoutingStrategy::valueOf),
        )
    }

    private companion object {
        const val TRANSFER = "transfer"
    }
}
