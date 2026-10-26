package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.Snapshots
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.ImageOverride
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.server
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.utils.Serialization
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PodSpecBuilderTest {
    private val builder = PodSpecBuilder(HETZNER)
    private val lobby = GroupId("lobby")

    @Test
    fun `a dynamic Paper server on the game pool`() {
        Snapshots.assertMatches("pod-dynamic-paper", yaml(build(GroupType.DYNAMIC, "bedwars")))
    }

    @Test
    fun `a default-group PandaSpigot lobby`() {
        Snapshots.assertMatches(
            "pod-default-pandaspigot",
            yaml(build(GroupType.DYNAMIC, "lobby", profile = "pandaspigot-1.8.8")),
        )
    }

    @Test
    fun `a Velocity proxy`() {
        Snapshots.assertMatches("pod-proxy-velocity", yaml(build(GroupType.PROXY, "proxy")))
    }

    @Test
    fun `a STATIC server with its volume claim`() {
        Snapshots.assertMatches("pod-static-paper", yaml(build(GroupType.STATIC, "smp")))
        Snapshots.assertMatches("pvc-static", yaml(builder.staticVolumeClaim(group(GroupType.STATIC, id = "smp"))))
    }

    @Test
    fun `pods carry no infrastructure credentials`() {
        GroupType.entries.forEach { type ->
            val spec = build(type, "g-${type.name.lowercase()}").spec
            val containers = spec.containers + spec.initContainers

            spec.automountServiceAccountToken shouldBe false
            containers.flatMap { it.env }.filter { it.valueFrom != null }.shouldBeEmpty()
            containers.flatMap { it.envFrom }.shouldBeEmpty()
            spec.volumes.mapNotNull { it.secret?.secretName } shouldContainExactly listOf(HETZNER.forwardingSecret)
            spec.volumes
                .flatMap { it.projected?.sources.orEmpty() }
                .map { it.serviceAccountToken.audience } shouldContainExactly listOf(HETZNER.gatewayAudience)
        }
    }

    @Test
    fun `the group type label is the group's real type`() {
        build(GroupType.STATIC, "smp").metadata.labels[PodLabels.GROUP_TYPE] shouldBe "static"
    }

    @Test
    fun `minikube pods have no game-pool scheduling`() {
        val spec = PodSpecBuilder(HETZNER.copy(gamePool = null)).let { build(GroupType.DYNAMIC, "bedwars", it) }.spec

        spec.tolerations.shouldBeEmpty()
        spec.affinity shouldBe null
    }

    @Test
    fun `image overrides must match an allowed prefix`() {
        val allowed =
            group(
                id = "custom",
            ).copy(imageOverride = ImageOverride("registry.example.com/paper:custom", "pull"))
        val denied = allowed.copy(imageOverride = ImageOverride("docker.io/evil/paper:latest", null))
        val profile = profile("paper-26.3")
        val withAllowList = PodSpecBuilder(HETZNER.copy(allowedImagePrefixes = listOf("registry.example.com/")))

        withAllowList.image(allowed, profile) shouldBe "registry.example.com/paper:custom"
        shouldThrow<IllegalArgumentException> { withAllowList.image(denied, profile) }
        shouldThrow<IllegalArgumentException> { builder.image(allowed, profile) }
    }

    @Test
    fun `a server is never built into another group's pod`() {
        shouldThrow<IllegalArgumentException> {
            builder.pod(server(group = "bedwars"), group(id = "skywars"), profile("paper-26.3"), lobby)
        }
    }

    private fun build(
        type: GroupType,
        groupId: String,
        with: PodSpecBuilder = builder,
        profile: String? = null,
    ): Pod {
        val group = group(type, id = groupId).let { if (profile != null) it.copy(profileId = profile) else it }
        val server =
            server(state = ServerState.REQUESTED, groupType = type, group = groupId, profileId = group.profileId)
        return with.pod(server, group, profile(group.profileId), lobby)
    }

    private fun profile(id: String) = RuntimeProfile.find(id) ?: error("Unknown profile $id")

    private fun yaml(resource: Any): String = Serialization.asYaml(resource)

    private companion object {
        val HETZNER =
            PodProperties(
                imageRegistry = "ghcr.io/jevzo/ogcloud",
                platformVersion = "2.0.0",
                gatewayAddress = "ogcloud-gateway.ogcloud.svc:9443",
                gamePool = PodProperties.GamePool("cloud.ogwars.io/pool", "game", "cloud.ogwars.io/game", "true"),
            )
    }
}
