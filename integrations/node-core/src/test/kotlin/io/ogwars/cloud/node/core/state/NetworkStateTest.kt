package io.ogwars.cloud.node.core.state

import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NetworkStateTest {
    private val state = NetworkState()

    private fun settings(
        version: Long,
        maintenance: Boolean,
    ) = NetworkSettingsUpdated
        .newBuilder()
        .setVersion(version)
        .setMaintenance(maintenance)
        .build()

    private fun group(
        version: Long,
        maintenance: Boolean,
        deleted: Boolean = false,
    ) = GroupUpdated
        .newBuilder()
        .setVersion(version)
        .setGroupId("lobby")
        .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
        .setMaintenance(maintenance)
        .setDeleted(deleted)
        .build()

    @Test
    fun `older settings never replace newer ones`() {
        state.update(settings(2, maintenance = true)) shouldBe true
        state.update(settings(1, maintenance = false)) shouldBe false

        state.settings.maintenance shouldBe true
    }

    @Test
    fun `group flags follow the newest update, and a deletion forgets the group`() {
        state.know("lobby", GroupType.GROUP_TYPE_DYNAMIC, maintenance = true)
        state.update(group(3, maintenance = false)) shouldBe true
        state.update(group(2, maintenance = true)) shouldBe false
        state.inMaintenance("lobby") shouldBe false
        state.groupType("lobby") shouldBe GroupType.GROUP_TYPE_DYNAMIC

        state.update(group(0, maintenance = false, deleted = true)) shouldBe true
        state.groupType("lobby") shouldBe null
    }

    @Test
    fun `what the welcome says yields to a broadcast already held`() {
        state.update(group(4, maintenance = false))
        state.know("lobby", GroupType.GROUP_TYPE_DYNAMIC, maintenance = true)

        state.inMaintenance("lobby") shouldBe false
    }
}
