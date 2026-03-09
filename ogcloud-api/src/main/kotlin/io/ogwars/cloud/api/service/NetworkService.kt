package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.NetworkSettingsResponse
import io.ogwars.cloud.api.dto.NetworkStatusResponse
import io.ogwars.cloud.api.dto.UpdateNetworkRequest
import io.ogwars.cloud.api.dto.toResponse
import io.ogwars.cloud.api.kafka.NetworkUpdateProducer
import io.ogwars.cloud.api.model.GeneralSettings
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.NetworkSettingsDocument
import io.ogwars.cloud.api.redis.ServerRedisRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service

@Service
class NetworkService(
    private val mongoTemplate: MongoTemplate,
    private val networkUpdateProducer: NetworkUpdateProducer,
    private val serverRedisRepository: ServerRedisRepository,
    private val auditLogService: AuditLogService,
) {
    fun getSettings(): NetworkSettingsResponse = findOrDefault().toResponse()

    fun getGeneralSettings(): GeneralSettings = findOrDefault().general

    fun isPermissionSystemEnabled(): Boolean = getGeneralSettings().permissionSystemEnabled

    fun updateSettings(request: UpdateNetworkRequest): NetworkSettingsResponse {
        val current = findOrDefault()

        val updatedMotd =
            request.motd?.let { req ->
                current.motd.copy(
                    global = req.global ?: current.motd.global,
                    maintenance = req.maintenance ?: current.motd.maintenance,
                )
            } ?: current.motd

        val updatedVersionName =
            request.versionName?.let { req ->
                current.versionName.copy(
                    global = req.global ?: current.versionName.global,
                    maintenance = req.maintenance ?: current.versionName.maintenance,
                )
            } ?: current.versionName

        val updatedTablist =
            request.tablist?.let { req ->
                current.tablist.copy(
                    header = req.header ?: current.tablist.header,
                    footer = req.footer ?: current.tablist.footer,
                )
            } ?: current.tablist

        val updatedGeneral =
            request.general?.let { req ->
                current.general.copy(
                    permissionSystemEnabled =
                        req.permissionSystemEnabled
                            ?: current.general.permissionSystemEnabled,
                    tablistEnabled = req.tablistEnabled ?: current.general.tablistEnabled,
                    proxyRoutingStrategy = req.proxyRoutingStrategy ?: current.general.proxyRoutingStrategy,
                )
            } ?: current.general

        val updated =
            current.copy(
                motd = updatedMotd,
                versionName = updatedVersionName,
                maxPlayers = request.maxPlayers ?: current.maxPlayers,
                defaultGroup = request.defaultGroup ?: current.defaultGroup,
                maintenance = request.maintenance ?: current.maintenance,
                maintenanceKickMessage = request.maintenanceKickMessage ?: current.maintenanceKickMessage,
                tablist = updatedTablist,
                general = updatedGeneral,
            )
        mongoTemplate.save(updated, COLLECTION)

        networkUpdateProducer.publishNetworkUpdate(updated)

        auditLogService.logApiAction(
            action = "NETWORK_SETTINGS_UPDATED",
            targetType = "NETWORK",
            targetId = "global",
            summary = "Updated network settings",
            metadata =
                mapOf(
                    "maintenance" to updated.maintenance.toString(),
                    "defaultGroup" to updated.defaultGroup,
                    "maxPlayers" to updated.maxPlayers.toString(),
                    "permissionSystemEnabled" to updated.general.permissionSystemEnabled.toString(),
                    "tablistEnabled" to updated.general.tablistEnabled.toString(),
                    "proxyRoutingStrategy" to updated.general.proxyRoutingStrategy.name,
                ),
        )

        return updated.toResponse()
    }

    fun setMaintenance(enabled: Boolean): NetworkSettingsResponse {
        val updated = findOrDefault().copy(maintenance = enabled)
        mongoTemplate.save(updated, COLLECTION)

        networkUpdateProducer.publishNetworkUpdate(updated)

        auditLogService.logApiAction(
            action = "NETWORK_MAINTENANCE_UPDATED",
            targetType = "NETWORK",
            targetId = "global",
            summary = "Set network maintenance=$enabled",
            metadata = mapOf("maintenance" to enabled.toString()),
        )

        return updated.toResponse()
    }

    fun getStatus(): NetworkStatusResponse {
        val servers = serverRedisRepository.findAll()
        val onlinePlayers = servers.filter { it.type == GroupType.PROXY }.sumOf { it.playerCount }
        val proxyCount = servers.count { it.type == GroupType.PROXY }
        val serverCount = servers.count { it.type != GroupType.PROXY }

        return NetworkStatusResponse(
            onlinePlayers = onlinePlayers,
            serverCount = serverCount,
            proxyCount = proxyCount,
        )
    }

    private fun findOrDefault(): NetworkSettingsDocument =
        mongoTemplate.findById<NetworkSettingsDocument>("global", COLLECTION)
            ?: NetworkSettingsDocument()

    companion object {
        private const val COLLECTION = "network_settings"
    }
}
