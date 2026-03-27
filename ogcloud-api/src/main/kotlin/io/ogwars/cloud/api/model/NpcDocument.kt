package io.ogwars.cloud.api.model

import io.ogwars.cloud.common.model.NpcClickAction
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLocation
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "npcs")
data class NpcDocument(
    @Id val id: String,
    val group: String,
    val location: NpcLocation,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel = NpcModel.STEVE,
    val skin: NpcSkin? = null,
    val lookAt: NpcLookAtConfig = NpcLookAtConfig(),
    val leftAction: NpcClickAction = NpcClickAction(),
    val rightAction: NpcClickAction = NpcClickAction(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

fun NpcDocument.toDefinition(): NpcDefinition =
    NpcDefinition(
        id = id,
        group = group,
        location = location,
        title = title,
        subtitle = subtitle,
        model = model,
        skin = skin,
        lookAt = lookAt,
        leftAction = leftAction,
        rightAction = rightAction,
    )
