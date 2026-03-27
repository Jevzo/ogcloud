package io.ogwars.cloud.server.api

import io.ogwars.cloud.common.model.NpcModel
import org.bukkit.Location
import java.util.function.Consumer

interface OgCloudRuntimeNpcHandle {
    fun getId(): String

    fun getLocation(): Location

    fun teleport(location: Location)

    fun setTitle(title: String?)

    fun setSubtitle(subtitle: String?)

    fun setModel(model: NpcModel)

    fun skin(
        textureValue: String,
        textureSignature: String? = null,
    )

    fun clearSkin()

    fun setLookAt(
        enabled: Boolean,
        radius: Double = 4.0,
    )

    fun subscribeLeftClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudSubscription

    fun subscribeRightClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudSubscription

    fun despawn()
}
