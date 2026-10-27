package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@PersistenceTest
class SettingsAndPermissionsTest(
    @Autowired private val settings: NetworkSettingsStore,
    @Autowired private val permissionGroups: PermissionGroupStore,
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
    fun `permission groups keep their nodes and descriptions`() {
        val vip = rank("vip", weight = 200, permissions = mapOf("ogcloud.fly" to "Fly in lobbies"))
        permissionGroups.save(vip)

        permissionGroups.find("vip")?.value shouldBe vip
        permissionGroups.findAll().map { it.value.id } shouldContainExactly listOf("vip", "default")
    }

    @Test
    fun `the seeded default group can be updated`() {
        val renamed = permissionGroups.findDefault().value.copy(name = "Member")

        permissionGroups.save(renamed)

        permissionGroups.findDefault().value shouldBe renamed
    }

    @Test
    fun `making a group the default demotes the previous default`() {
        permissionGroups.save(rank("member", weight = 50, isDefault = true))

        permissionGroups.findDefault().value.id shouldBe "member"
        permissionGroups.find("default")?.value?.isDefault shouldBe false
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
