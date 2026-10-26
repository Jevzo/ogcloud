package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.ImageOverride
import io.ogwars.cloud.domain.group.ResourceSpec
import io.ogwars.cloud.domain.group.ScalingPolicy
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.UUID

@PersistenceTest
class GroupStoreTest(
    @Autowired private val groups: GroupStore,
    @Autowired private val templates: TemplateVersionStore,
) {
    private val lobby =
        Group(
            id = GroupId("lobby"),
            type = GroupType.DYNAMIC,
            profileId = "paper-26.3",
            scaling = ScalingPolicy(2, 10, 50, 0.8, 0.3, Duration.ofSeconds(60)),
            drainTimeout = Duration.ofSeconds(45),
            maintenance = false,
            resources = ResourceSpec("2Gi", "3Gi", "1", "2"),
            jvmFlags = "-XX:+UseG1GC",
            imageOverride = ImageOverride("ghcr.io/acme/lobby:1", "acme-pull"),
        )

    @Test
    fun `round-trips every field`() {
        groups.create(lobby)

        groups.find(GroupId("lobby")) shouldBe lobby
    }

    @Test
    fun `update overwrites the stored group`() {
        groups.create(lobby)
        val changed = lobby.copy(maintenance = true, scaling = lobby.scaling.copy(minOnline = 4))

        groups.update(changed)

        groups.find(GroupId("lobby")) shouldBe changed
    }

    @Test
    fun `soft-deleted groups disappear until restored`() {
        groups.create(lobby)

        groups.softDelete(lobby.id, NOW)
        groups.findAll().shouldBeEmpty()
        groups.deletedBefore(NOW.plusSeconds(1)) shouldContainExactly listOf(lobby.id)

        groups.restore(lobby.id)
        groups.findAll() shouldContainExactly listOf(lobby)
    }

    @Test
    fun `purge removes the group with its template versions`() {
        groups.create(lobby)
        val version =
            TemplateVersion(
                UUID.randomUUID(),
                lobby.id,
                "templates/lobby/1.tar.gz",
                "a".repeat(64),
                10,
                TemplateSource.UPLOAD,
                Actor.system("test"),
                NOW,
            )
        templates.add(version)

        groups.purge(lobby.id)

        groups.find(lobby.id) shouldBe null
        templates.findByGroup(lobby.id).shouldBeEmpty()
    }
}
