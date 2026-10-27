package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

@PersistenceTest
class SettingsAndPermissionsTest(
    @Autowired private val settings: NetworkSettingsStore,
    @Autowired private val permissionGroups: PermissionGroupStore,
    @Autowired private val players: PlayerStore,
) {
    @Test
    fun `network settings start from the seeded defaults and save changes`() {
        val seeded = settings.load()
        seeded.value.defaultGroupId shouldBe GroupId("lobby")
        seeded.value.eulaAccepted shouldBe false

        val changed =
            seeded.value.copy(
                maintenance = true,
                routingStrategy = RoutingStrategy.ROUND_ROBIN,
                eulaAcceptedAt = NOW,
            )
        settings.save(seeded.revision, changed)

        settings.load().value shouldBe changed
        shouldThrow<StaleUpdateException> { settings.save(seeded.revision, changed.copy(maxPlayers = 10)) }
    }

    @Test
    fun `a new default group must exist, the seeded one need not`() {
        val seeded = settings.load()

        shouldThrow<IllegalArgumentException> {
            settings.save(seeded.revision, seeded.value.copy(defaultGroupId = GroupId("missing")))
        }
        settings.save(seeded.revision, seeded.value.copy(maxPlayers = 10))
    }

    @Test
    fun `permission groups keep their nodes and descriptions`() {
        val vip = rank("vip", weight = 200, permissions = mapOf("ogcloud.fly" to "Fly in lobbies"))
        permissionGroups.create(vip)

        permissionGroups.find("vip")?.value shouldBe vip
        permissionGroups.findAll().map { it.value.id } shouldContainExactly listOf("vip", "default")
    }

    @Test
    fun `the seeded default group can be updated`() {
        val seeded = permissionGroups.findDefault()
        val renamed = seeded.value.copy(name = "Member")

        permissionGroups.update(seeded.revision, renamed)

        permissionGroups.findDefault().value shouldBe renamed
        shouldThrow<StaleUpdateException> { permissionGroups.update(seeded.revision, renamed.copy(weight = 1)) }
        shouldThrow<ConflictException> { permissionGroups.update(seeded.revision + 1, renamed.copy(isDefault = false)) }
    }

    @Test
    fun `making a group the default demotes the previous default`() {
        permissionGroups.create(rank("member", weight = 50, isDefault = true))

        permissionGroups.findDefault().value.id shouldBe "member"
        permissionGroups.find("default")?.value?.isDefault shouldBe false
    }

    @Test
    fun `deleting a group moves its holders to the default group`() {
        val holder = UUID(5, 1)
        permissionGroups.create(rank("vip", weight = 200))
        players.recordSeen(holder, "Holder", NOW, PlayerRank(holder, "vip", NOW.plusSeconds(3600)))

        permissionGroups.delete("vip") shouldContainExactly listOf(holder)

        players.find(holder)?.rank shouldBe PlayerRank(holder, "default", null)
        permissionGroups.find("vip") shouldBe null
        shouldThrow<ConflictException> { permissionGroups.delete("default") }
        shouldThrow<ConflictException> { permissionGroups.create(rank("default", weight = 1)) }
    }

    private fun rank(
        id: String,
        weight: Int,
        permissions: Map<String, String> = emptyMap(),
        isDefault: Boolean = false,
    ) = PermissionGroup(
        id,
        id.replaceFirstChar(Char::uppercase),
        weight,
        permissions,
        RankDisplay(chatPrefix = "&6[$id] "),
        isDefault,
    )
}
