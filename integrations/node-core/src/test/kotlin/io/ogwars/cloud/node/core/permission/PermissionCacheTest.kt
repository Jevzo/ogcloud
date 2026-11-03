package io.ogwars.cloud.node.core.permission

import io.ogwars.cloud.contracts.events.v1.PermissionGroupUpdated
import io.ogwars.cloud.contracts.events.v1.PlayerPermissionsUpdated
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.query.FakeQueries
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class PermissionCacheTest {
    private val queries = FakeQueries()
    private val cache = PermissionCache(queries)
    private val steve = UUID.randomUUID()

    private fun group(
        id: String,
        version: Long,
        vararg nodes: String,
    ) = PermissionGroup
        .newBuilder()
        .setPermissionGroupId(id)
        .setVersion(version)
        .addAllPermissions(nodes.toList())
        .build()

    private fun profile(
        group: String,
        version: Long,
    ) = PlayerProfile
        .newBuilder()
        .setPlayerUuid(steve.toString())
        .setPermissionGroupId(group)
        .setVersion(version)
        .build()

    init {
        queries.permissionGroups["default"] = group("default", 1, "lobby.join")
        queries.permissionGroups["admin"] = group("admin", 1, "*")
    }

    @Test
    fun `a loaded rank decides permissions, the platform decides with the permission system off`() {
        cache.load(steve, profile("default", 1)).join()

        cache.decide(steve, "lobby.join", permissionSystemEnabled = true) shouldBe Decision.ALLOW
        cache.decide(steve, "ogcloud.admin.server", permissionSystemEnabled = true) shouldBe Decision.DENY
        cache.decide(steve, "lobby.join", permissionSystemEnabled = false) shouldBe Decision.DEFAULT
        cache.decide(UUID.randomUUID(), "lobby.join", permissionSystemEnabled = true) shouldBe Decision.DENY
    }

    @Test
    fun `the wildcard grants everything`() {
        cache.load(steve, profile("admin", 1)).join()

        cache.decide(steve, "anything.at.all", permissionSystemEnabled = true) shouldBe Decision.ALLOW
    }

    @Test
    fun `a backend asks the gateway for the profile, a rank change reloads it, an older one does not`() {
        queries.profiles[steve] = profile("default", 2)
        cache.load(steve).join()

        queries.profiles[steve] = profile("admin", 3)
        cache.refresh(update(version = 1)).join() shouldBe null
        cache.rank(steve)?.group?.permissionGroupId shouldBe "default"
        cache
            .refresh(update(version = 3))
            .join()
            ?.group
            ?.permissionGroupId shouldBe "admin"
    }

    @Test
    fun `a changed permission group reaches every holder`() {
        cache.load(steve, profile("default", 1)).join()
        queries.permissionGroups["default"] = group("default", 2, "lobby.join", "lobby.fly")

        val changed = cache.refresh(PermissionGroupUpdated.newBuilder().setPermissionGroupId("default").build()).join()

        changed shouldContainExactly setOf(steve)
        cache.decide(steve, "lobby.fly", permissionSystemEnabled = true) shouldBe Decision.ALLOW
    }

    @Test
    fun `a rank that loads after its player left is dropped`() {
        queries.profiles[steve] = profile("default", 1)
        val loading = cache.load(steve)
        cache.unload(steve)
        loading.join()

        cache.rank(steve) shouldBe null
        cache.load(steve).join()
        cache.rank(steve)?.group?.permissionGroupId shouldBe "default"
    }

    private fun update(version: Long) =
        PlayerPermissionsUpdated
            .newBuilder()
            .setPlayerUuid(steve.toString())
            .setVersion(version)
            .build()
}
