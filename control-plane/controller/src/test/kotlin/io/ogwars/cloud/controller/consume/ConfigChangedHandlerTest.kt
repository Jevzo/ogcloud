package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.contracts.events.v1.EntityChanged
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast.UpdateCase
import io.ogwars.cloud.contracts.events.v1.PlayerRankChanged
import io.ogwars.cloud.contracts.node.v1.ListNpcsResponse
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.readmodel.InMemoryNpcStore
import io.ogwars.cloud.controller.readmodel.InMemoryPermissionGroupStore
import io.ogwars.cloud.controller.readmodel.InMemoryPlayerStore
import io.ogwars.cloud.controller.readmodel.PlayerPermissions
import io.ogwars.cloud.controller.readmodel.RecordingBroadcasts
import io.ogwars.cloud.controller.readmodel.permissionGroup
import io.ogwars.cloud.controller.template.InMemoryTemplateVersionStore
import io.ogwars.cloud.controller.template.TemplateVersions
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class ConfigChangedHandlerTest {
    private val f = LifecycleFixture(listOf(group(id = "lobby"), group(id = "bedwars")))
    private val readModels = ValkeyReadModelStore(TestValkey.connection)
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val permissionGroups =
        InMemoryPermissionGroupStore(
            permissionGroup("default", isDefault = true),
            permissionGroup(
                "vip",
                permissions =
                    mapOf("b.node" to "", "a.node" to ""),
            ),
        )
    private val npcs = InMemoryNpcStore()
    private val players = InMemoryPlayerStore()
    private val broadcasts = RecordingBroadcasts()
    private val templates =
        TemplateVersions(
            f.servers,
            f.groupStore,
            InMemoryTemplateVersionStore(),
            readModels,
            f.pods,
            { UUID.randomUUID() },
            f.clock,
        )
    private val permissions = PlayerPermissions(players, permissionGroups, sessions, readModels, broadcasts)
    private val handler =
        ConfigChangedHandler(
            f.groupStore,
            f.network,
            permissionGroups,
            npcs,
            readModels,
            broadcasts,
            permissions,
            sessions,
            templates,
        )

    @Test
    fun `a group change refreshes its read model, a deletion removes it`() {
        handler.handle(change("bedwars") { setGroup(entity("bedwars")) })

        GroupUpdated.parseFrom(readModels.get(ReadModelKind.GROUP, "bedwars")).groupId shouldBe "bedwars"
        f.removeGroup(GroupId("bedwars"))
        handler.handle(change("bedwars") { setGroup(entity("bedwars", deleted = true)) })

        readModels.get(ReadModelKind.GROUP, "bedwars") shouldBe null
        broadcasts.sent.map { it.group.deleted } shouldContainExactly listOf(false, true)
    }

    @Test
    fun `re-enabling the permission system re-sends every online player's rank`() {
        val online = UUID.randomUUID()
        players.recordSeen(online, "Steve", f.clock.instant(), PlayerRank(online, "vip", null))
        players.updateRank(PlayerRank(online, "vip", null))
        sessions.put(PlayerSession(online, "Steve", UUID.randomUUID(), null, "vip", 0, f.clock.instant()))
        val settings = f.network.load()
        f.network.save(settings.revision, settings.value.copy(permissionSystemEnabled = false))
        handler.handle(change("network") { setNetworkSettings(entity("network")) })
        broadcasts.sent.clear()

        val disabled = f.network.load()
        f.network.save(disabled.revision, disabled.value.copy(permissionSystemEnabled = true))
        handler.handle(change("network") { setNetworkSettings(entity("network")) })

        NetworkSettingsUpdated
            .parseFrom(
                readModels.get(ReadModelKind.NETWORK_SETTINGS, "network"),
            ).permissionSystemEnabled shouldBe
            true
        broadcasts.sent.map { it.updateCase } shouldContainExactly
            listOf(UpdateCase.NETWORK_SETTINGS, UpdateCase.PLAYER_PERMISSIONS)
        sessions.get(online)?.value?.rankVersion shouldBe 1
    }

    @Test
    fun `permission groups list their nodes in a stable order`() {
        handler.handle(change("vip") { setPermissionGroup(entity("vip")) })

        PermissionGroup
            .parseFrom(
                readModels.get(ReadModelKind.PERMISSION_GROUP, "vip"),
            ).permissionsList shouldContainExactly
            listOf("a.node", "b.node")
        permissionGroups.delete("vip")
        handler.handle(change("vip") { setPermissionGroup(entity("vip", deleted = true)) })
        readModels.get(ReadModelKind.PERMISSION_GROUP, "vip") shouldBe null
    }

    @Test
    fun `a rank change reaches online players only through their session`() {
        val online = UUID.randomUUID()
        val offline = UUID.randomUUID()
        listOf(online, offline).forEach {
            players.recordSeen(it, "p$it".take(16), f.clock.instant(), PlayerRank(it, "default", null))
        }
        sessions.put(PlayerSession(online, "Steve", UUID.randomUUID(), null, "default", 0, f.clock.instant()))
        listOf(online, offline).forEach { players.updateRank(PlayerRank(it, "vip", null)) }

        listOf(
            online,
            offline,
        ).forEach { player -> handler.handle(change(player.toString()) { setPlayerRank(rank(player)) }) }

        PlayerProfile
            .parseFrom(
                readModels.get(ReadModelKind.PLAYER_PROFILE, offline.toString()),
            ).permissionGroupId shouldBe
            "vip"
        sessions.get(online)?.value?.permissionGroupId shouldBe "vip"
        broadcasts.sent.map { it.playerPermissions.playerUuid } shouldContainExactly listOf(online.toString())
    }

    @Test
    fun `an NPC change rebuilds its group's NPC list`() {
        val npc = Npc(UUID.randomUUID(), GroupId("lobby"), Location("world", 1.0, 64.0, 2.0), title = "Bedwars")
        npcs.save(npc)

        handler.handle(change("lobby") { setNpc(entity(npc.id.toString())) })

        ListNpcsResponse.parseFrom(readModels.get(ReadModelKind.NPCS_BY_GROUP, "lobby")).npcsList.map {
            it.title
        } shouldContainExactly
            listOf("Bedwars")
    }

    @Test
    fun `an empty change is not retried`() {
        shouldThrow<NonRetryableEventException> { handler.handle(change("network") {}) }
    }

    private fun change(
        subject: String,
        body: ConfigChanged.Builder.() -> Unit,
    ): ConfigChanged =
        ConfigChanged
            .newBuilder()
            .setSubjectId(subject)
            .apply(body)
            .build()

    private fun entity(
        id: String,
        deleted: Boolean = false,
    ) = EntityChanged
        .newBuilder()
        .setId(id)
        .setDeleted(deleted)
        .build()

    private fun rank(player: UUID) =
        PlayerRankChanged
            .newBuilder()
            .setPlayerUuid(player.toString())
            .setPermissionGroupId("vip")
            .build()
}
