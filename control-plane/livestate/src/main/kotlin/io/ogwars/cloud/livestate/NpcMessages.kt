package io.ogwars.cloud.livestate

import io.ogwars.cloud.contracts.node.v1.NpcTransfer
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.npc.NpcSkin
import java.util.UUID
import io.ogwars.cloud.contracts.node.v1.Location as LocationMessage
import io.ogwars.cloud.contracts.node.v1.Npc as NpcMessage
import io.ogwars.cloud.contracts.node.v1.NpcAction as NpcActionMessage

// Managed NPCs as nodes receive them (D-0077) and as in-game edits send them (D-0129).

fun Npc.toProto(): NpcMessage =
    NpcMessage
        .newBuilder()
        .setNpcId(id.toString())
        .setGroupId(groupId.value)
        .setLocation(
            LocationMessage
                .newBuilder()
                .setWorld(location.world)
                .setX(location.x)
                .setY(location.y)
                .setZ(location.z)
                .setYaw(location.yaw)
                .setPitch(location.pitch),
        ).setTitle(title)
        .setSubtitle(subtitle)
        .setSkinTexture(skin?.texture.orEmpty())
        .setSkinSignature(skin?.signature.orEmpty())
        .setLookAtPlayers(lookAtPlayers)
        // Labelled: inside the builder, leftClick and rightClick would be the builder's own fields.
        .apply { this@toProto.leftClick?.let { setLeftClick(it.toProto()) } }
        .apply { this@toProto.rightClick?.let { setRightClick(it.toProto()) } }
        .build()

/** Throws IllegalArgumentException for a malformed id or group. */
fun NpcMessage.toDomain(): Npc =
    Npc(
        UUID.fromString(npcId),
        GroupId(groupId),
        Location(location.world, location.x, location.y, location.z, location.yaw, location.pitch),
        title,
        subtitle,
        skinTexture.takeIf(String::isNotEmpty)?.let { NpcSkin(it, skinSignature) },
        lookAtPlayers,
        leftClick.takeIf { hasLeftClick() }?.toDomain(),
        rightClick.takeIf { hasRightClick() }?.toDomain(),
    )

private fun NpcAction.toProto(): NpcActionMessage =
    when (this) {
        is NpcAction.Transfer -> {
            NpcActionMessage
                .newBuilder()
                .setTransfer(
                    NpcTransfer
                        .newBuilder()
                        .setGroupId(groupId.value)
                        .apply { this@toProto.routingStrategy?.let { setRoutingStrategy(it.toProto()) } },
                ).build()
        }
    }

/** An action without a known kind means no action. */
private fun NpcActionMessage.toDomain(): NpcAction? =
    when (actionCase) {
        NpcActionMessage.ActionCase.TRANSFER -> {
            NpcAction.Transfer(
                GroupId(transfer.groupId),
                transfer.routingStrategy.toDomain(),
            )
        }

        NpcActionMessage.ActionCase.ACTION_NOT_SET, null -> {
            null
        }
    }
