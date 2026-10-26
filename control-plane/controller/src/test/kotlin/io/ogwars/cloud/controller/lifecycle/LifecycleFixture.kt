package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.crashloop.CrashLoopPolicy
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.server.LifecyclePolicy
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.livestate.ValkeyCrashFailureStore
import io.ogwars.cloud.livestate.ValkeyServerStateStore
import java.util.UUID

/** The lifecycle wired on a real Valkey with recording fakes for Kubernetes and Kafka. */
class LifecycleFixture(
    groups: List<Group>,
    eulaAccepted: Boolean = true,
    maintenance: Boolean = false,
    val properties: LifecycleProperties = LifecycleProperties(),
) {
    val clock = MutableClock(T0)
    val servers = ValkeyServerStateStore(TestValkey.connection)
    val failures = ValkeyCrashFailureStore(TestValkey.connection)
    val pods = RecordingPodGateway()
    val events = RecordingServerEvents()
    val groupStore = InMemoryGroupStore(groups)
    val network = FixedNetworkSettingsStore(settings(eulaAccepted, maintenance))
    val policy = LifecyclePolicy(properties.timeouts)
    val breaker = CrashLoopBreaker(failures, CrashLoopPolicy(properties.crashLoop), properties, events, clock)
    val lifecycle = LifecycleService(servers, groupStore, pods, events, breaker, policy, clock)
    val builder = PodSpecBuilder(PodProperties("ghcr.io/jevzo/ogcloud", "2.0.0", "ogcloud-gateway.ogcloud.svc:9443"))

    init {
        TestValkey.flush()
    }

    fun removeGroup(id: GroupId) = groupStore.softDelete(id, clock.instant())

    fun request(group: Group): UUID {
        val id = UUID.randomUUID()
        lifecycle.request(id, group.id, group.type, group.profileId, Actor.system("test"))
        return id
    }

    fun state(id: UUID) = servers.get(id)?.value?.state

    private fun settings(
        eulaAccepted: Boolean,
        maintenance: Boolean,
    ) = NetworkSettings(
        maintenance = maintenance,
        motd = "OgCloud",
        maintenanceMotd = "Maintenance",
        versionName = "OgCloud",
        maintenanceVersionName = "Maintenance",
        maxPlayers = 1000,
        defaultGroupId = GroupId("lobby"),
        routingStrategy = RoutingStrategy.entries.first(),
        permissionSystemEnabled = true,
        tablistEnabled = false,
        tablistHeader = "",
        tablistFooter = "",
        maintenanceKickMessage = "Maintenance",
        eulaAcceptedAt = if (eulaAccepted) T0 else null,
        requireMinecraftLink = false,
    )
}
