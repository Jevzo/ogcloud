package io.ogwars.cloud.api.network

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.groups.RestartRequest
import io.ogwars.cloud.api.jobs.JobView
import io.ogwars.cloud.api.jobs.toView
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The settings an operator edits; texts use `&` colour codes. */
data class NetworkSettingsFields(
    val maintenance: Boolean,
    val motd: String,
    val maintenanceMotd: String,
    val versionName: String,
    val maintenanceVersionName: String,
    val maxPlayers: Int,
    val defaultGroupId: String,
    val routingStrategy: RoutingStrategy,
    val permissionSystemEnabled: Boolean,
    val tablistEnabled: Boolean,
    val tablistHeader: String,
    val tablistFooter: String,
    val maintenanceKickMessage: String,
    val requireMinecraftLink: Boolean,
) {
    /** The EULA acceptance is not edited here; [NetworkManagement.update] keeps the stored one. */
    fun toDomain() =
        NetworkSettings(
            maintenance = maintenance,
            motd = motd,
            maintenanceMotd = maintenanceMotd,
            versionName = versionName,
            maintenanceVersionName = maintenanceVersionName,
            maxPlayers = maxPlayers,
            defaultGroupId = GroupId(defaultGroupId),
            routingStrategy = routingStrategy,
            permissionSystemEnabled = permissionSystemEnabled,
            tablistEnabled = tablistEnabled,
            tablistHeader = tablistHeader,
            tablistFooter = tablistFooter,
            maintenanceKickMessage = maintenanceKickMessage,
            eulaAcceptedAt = null,
            requireMinecraftLink = requireMinecraftLink,
        )
}

data class NetworkSettingsView(
    val settings: NetworkSettingsFields,
    /** Set by an owner through `POST /api/v1/network/eula` or by the chart (D-0100); servers start only afterwards. */
    val eulaAcceptedAt: Instant?,
    /** Present it when updating; a stale one is refused with 409 (D-0069). */
    val version: Long,
)

data class UpdateNetworkSettingsRequest(
    val settings: NetworkSettingsFields,
    val version: Long,
)

private fun Revisioned<NetworkSettings>.toView() =
    with(value) {
        NetworkSettingsView(
            NetworkSettingsFields(
                maintenance,
                motd,
                maintenanceMotd,
                versionName,
                maintenanceVersionName,
                maxPlayers,
                defaultGroupId.value,
                routingStrategy,
                permissionSystemEnabled,
                tablistEnabled,
                tablistHeader,
                tablistFooter,
                maintenanceKickMessage,
                requireMinecraftLink,
            ),
            eulaAcceptedAt,
            revision,
        )
    }

@RestController
@RequestMapping("/api/v1/network")
class NetworkController(
    private val network: NetworkManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.NETWORK_READ)
    @GetMapping
    fun get(): NetworkSettingsView = network.get().toView()

    @Requires(ApiPermission.NETWORK_WRITE)
    @PutMapping
    fun update(
        @RequestBody request: UpdateNetworkSettingsRequest,
    ): NetworkSettingsView = network.update(actors.current(), request.version, request.settings.toDomain()).toView()

    /** Owners only, checked in [NetworkManagement.acceptEula]. */
    @AnyCaller
    @PostMapping("/eula")
    fun acceptEula(): NetworkSettingsView = network.acceptEula(actors.current()).toView()

    @Requires(ApiPermission.NETWORK_WRITE)
    @PostMapping("/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun restart(
        @RequestBody request: RestartRequest,
    ): JobView = network.restart(actors.current(), request.batchSize).toView()

    @Requires(ApiPermission.NETWORK_WRITE)
    @PostMapping("/forwarding/rotate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun rotateForwardingToken(): JobView = network.rotateForwardingToken(actors.current()).toView()
}
