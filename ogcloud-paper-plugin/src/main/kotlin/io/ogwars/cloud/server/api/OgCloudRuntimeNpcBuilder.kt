package io.ogwars.cloud.server.api

import io.ogwars.cloud.common.model.NpcModel
import org.bukkit.Location
import java.util.function.Consumer

interface OgCloudRuntimeNpcBuilder {
    fun location(location: Location): OgCloudRuntimeNpcBuilder

    fun title(title: String?): OgCloudRuntimeNpcBuilder

    fun subtitle(subtitle: String?): OgCloudRuntimeNpcBuilder

    fun model(model: NpcModel): OgCloudRuntimeNpcBuilder

    fun skin(
        textureValue: String,
        textureSignature: String? = null,
    ): OgCloudRuntimeNpcBuilder

    fun clearSkin(): OgCloudRuntimeNpcBuilder

    fun lookAt(
        enabled: Boolean,
        radius: Double = 4.0,
    ): OgCloudRuntimeNpcBuilder

    fun onLeftClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudRuntimeNpcBuilder

    fun onRightClick(listener: Consumer<OgCloudNpcInteraction>): OgCloudRuntimeNpcBuilder

    fun spawn(): OgCloudRuntimeNpcHandle
}
