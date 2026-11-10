package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GrafanaRoleTest {
    @Test
    fun `owners administer Grafana, readers of the overview look, everyone else is refused`() {
        GrafanaRole.of(user(BuiltInRole.OWNER.id), BuiltInRole.OWNER.permissions) shouldBe GrafanaRole.ADMIN
        GrafanaRole.of(user(BuiltInRole.ADMIN.id), BuiltInRole.ADMIN.permissions) shouldBe GrafanaRole.VIEWER
        GrafanaRole.of(user(BuiltInRole.VIEWER.id), BuiltInRole.VIEWER.permissions) shouldBe GrafanaRole.VIEWER
        GrafanaRole.of(user("builder"), setOf(ApiPermission.GROUPS_READ)) shouldBe null
    }

    // An unlinked user holds no permissions while the network requires the link (D-0094).
    @Test
    fun `an owner without effective permissions gets no role`() {
        GrafanaRole.of(user(BuiltInRole.OWNER.id), emptySet()) shouldBe null
    }

    private fun user(roleId: String) =
        WebUser(UUID.randomUUID(), "someone", "someone@example.com", null, roleId, Instant.EPOCH)
}
