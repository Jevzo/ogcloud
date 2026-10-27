package io.ogwars.cloud.api.npcs

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.npc.NpcSkin
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class LocationView(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class SkinView(
    val texture: String,
    val signature: String,
)

/** Sends the clicking player to a group; no routing strategy uses the network's. */
data class TransferActionView(
    val groupId: String,
    val routingStrategy: RoutingStrategy?,
)

data class NpcSettings(
    /** Every server of this group shows the NPC. */
    val groupId: String,
    val location: LocationView,
    /** `&` colour codes; `%players%` and `%servers%` show the live counts of the click target's group. */
    val title: String,
    val subtitle: String,
    val skin: SkinView?,
    val lookAtPlayers: Boolean,
    val leftClick: TransferActionView?,
    val rightClick: TransferActionView?,
) {
    fun toDomain(id: UUID) =
        Npc(
            id,
            GroupId(groupId),
            Location(location.world, location.x, location.y, location.z, location.yaw, location.pitch),
            title,
            subtitle,
            skin?.let { NpcSkin(it.texture, it.signature) },
            lookAtPlayers,
            leftClick?.toDomain(),
            rightClick?.toDomain(),
        )

    private fun TransferActionView.toDomain() = NpcAction.Transfer(GroupId(groupId), routingStrategy)
}

data class NpcView(
    val id: UUID,
    val settings: NpcSettings,
)

private fun Npc.toView() =
    NpcView(
        id,
        NpcSettings(
            groupId.value,
            LocationView(location.world, location.x, location.y, location.z, location.yaw, location.pitch),
            title,
            subtitle,
            skin?.let { SkinView(it.texture, it.signature) },
            lookAtPlayers,
            leftClick?.toView(),
            rightClick?.toView(),
        ),
    )

private fun NpcAction.toView() =
    when (this) {
        is NpcAction.Transfer -> TransferActionView(groupId.value, routingStrategy)
    }

/** Managed NPCs; every change reaches the servers of the affected groups (D-0104). */
@Service
class NpcManagement(
    private val npcs: NpcStore,
    private val groups: GroupStore,
    private val announcements: Announcements,
    private val ids: IdGenerator,
) {
    fun list(groupId: GroupId?): List<Npc> = groupId?.let(npcs::findByGroup) ?: npcs.findAll()

    fun get(id: UUID): Npc = npcs.find(id) ?: throw NoSuchElementException("NPC $id does not exist")

    fun create(
        actor: Actor,
        settings: NpcSettings,
    ): Npc {
        val npc = validated(settings.toDomain(ids.next()))
        npcs.save(npc)
        announcements.npc(actor, npc.groupId, npc.id)
        return npc
    }

    fun update(
        actor: Actor,
        id: UUID,
        settings: NpcSettings,
    ): Npc {
        val previous = get(id)
        val npc = validated(settings.toDomain(id))
        npcs.save(npc)
        // Moved to another group: the old group's servers drop it.
        if (previous.groupId != npc.groupId) announcements.npc(actor, previous.groupId, id, deleted = true)
        announcements.npc(actor, npc.groupId, id)
        return npc
    }

    fun delete(
        actor: Actor,
        id: UUID,
    ) {
        val npc = get(id)
        npcs.delete(id)
        announcements.npc(actor, npc.groupId, id, deleted = true)
    }

    private fun validated(npc: Npc): Npc {
        val targets =
            listOfNotNull(npc.leftClick, npc.rightClick).map { action ->
                when (action) {
                    is NpcAction.Transfer -> action.groupId
                }
            }
        (listOf(npc.groupId) + targets).forEach {
            groups.find(it)
                ?: throw IllegalArgumentException("Group $it does not exist")
        }
        return npc
    }
}

@RestController
@RequestMapping("/api/v1/npcs")
class NpcController(
    private val npcs: NpcManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.NPCS_READ)
    @GetMapping
    fun list(
        @RequestParam groupId: String?,
    ): List<NpcView> = npcs.list(groupId?.let(::GroupId)).map { it.toView() }

    @Requires(ApiPermission.NPCS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): NpcView = npcs.get(id).toView()

    @Requires(ApiPermission.NPCS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody settings: NpcSettings,
    ): NpcView = npcs.create(actors.current(), settings).toView()

    @Requires(ApiPermission.NPCS_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody settings: NpcSettings,
    ): NpcView = npcs.update(actors.current(), id, settings).toView()

    @Requires(ApiPermission.NPCS_WRITE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = npcs.delete(actors.current(), id)
}
