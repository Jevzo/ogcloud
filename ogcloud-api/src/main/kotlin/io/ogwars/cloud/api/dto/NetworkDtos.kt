package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.GeneralSettings
import io.ogwars.cloud.api.model.MotdSettings
import io.ogwars.cloud.api.model.NetworkSettingsDocument
import io.ogwars.cloud.api.model.ProxyRoutingStrategy
import io.ogwars.cloud.api.model.TablistSettings
import io.ogwars.cloud.api.model.VersionNameSettings
import jakarta.validation.Valid
import jakarta.validation.constraints.Min

data class NetworkSettingsResponse(
    val motd: MotdSettings,
    val versionName: VersionNameSettings,
    val maxPlayers: Int,
    val defaultGroup: String,
    val maintenance: Boolean,
    val maintenanceKickMessage: String,
    val tablist: TablistSettings,
    val general: GeneralSettings
)

data class UpdateMotdRequest(
    val global: String? = null,
    val maintenance: String? = null
)

data class UpdateVersionNameRequest(
    val global: String? = null,
    val maintenance: String? = null
)

data class UpdateTablistRequest(
    val header: String? = null,
    val footer: String? = null
)

data class UpdateGeneralSettingsRequest(
    val permissionSystemEnabled: Boolean? = null,
    val tablistEnabled: Boolean? = null,
    val proxyRoutingStrategy: ProxyRoutingStrategy? = null
)

data class UpdateNetworkRequest(
    @field:Valid val motd: UpdateMotdRequest? = null,
    @field:Valid val versionName: UpdateVersionNameRequest? = null,
    @field:Min(1) val maxPlayers: Int? = null,
    val defaultGroup: String? = null,
    val maintenance: Boolean? = null,
    val maintenanceKickMessage: String? = null,
    @field:Valid val tablist: UpdateTablistRequest? = null,
    @field:Valid val general: UpdateGeneralSettingsRequest? = null
)

data class MaintenanceToggleRequest(
    val maintenance: Boolean
)

data class NetworkStatusResponse(
    val onlinePlayers: Int,
    val serverCount: Int,
    val proxyCount: Int
)

fun NetworkSettingsDocument.toResponse(): NetworkSettingsResponse {
    return NetworkSettingsResponse(
        motd = motd,
        versionName = versionName,
        maxPlayers = maxPlayers,
        defaultGroup = defaultGroup,
        maintenance = maintenance,
        maintenanceKickMessage = maintenanceKickMessage,
        tablist = tablist,
        general = general
    )
}
