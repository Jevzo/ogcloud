package io.ogwars.cloud.api.ingame

import io.ogwars.cloud.api.groups.GROUPS_PATH
import io.ogwars.cloud.api.network.NETWORK_PATH
import io.ogwars.cloud.api.permissions.RANK_PATH
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.contracts.internal.v1.AdminChangeRequested
import io.ogwars.cloud.contracts.node.v1.GroupMaintenanceChange
import io.ogwars.cloud.contracts.node.v1.NetworkMaintenanceChange
import io.ogwars.cloud.contracts.node.v1.RankChange
import io.ogwars.cloud.contracts.toProtoDuration
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Maintenance and rank changes from in-game admins (D-0132). */
@ApiTest
class InGameAdminChangesTest(
    @Autowired private val groups: GroupStore,
    @Autowired private val network: NetworkSettingsStore,
    @Autowired private val players: PlayerStore,
    @Autowired private val audit: AuditLogStore,
    @Autowired private val publisher: EventPublisher,
    @Autowired private val metadata: EventMetadataFactory,
) {
    private val admin = UUID.randomUUID()

    @Test
    fun `maintenance and ranks change like dashboard edits, audited as the player`() {
        val lobby = "admin-${UUID.randomUUID().toString().take(SUFFIX)}"
        groups.create(group(id = lobby))
        val player = UUID.randomUUID()
        val forever = UUID.randomUUID()
        players.recordSeen(player, "Steve", Instant.now(), PlayerRank(player, DEFAULT_GROUP, null))
        players.recordSeen(
            forever,
            "Alex",
            Instant.now(),
            PlayerRank(forever, DEFAULT_GROUP, Instant.now().plus(Duration.ofDays(1))),
        )
        val sent = Instant.now()

        publish(lobby) { setGroupMaintenance(GroupMaintenanceChange.newBuilder().setGroupId(lobby).setEnabled(true)) }
        publish(player.toString()) {
            setRank(
                RankChange
                    .newBuilder()
                    .setPlayerUuid(player.toString())
                    .setPermissionGroupId(DEFAULT_GROUP)
                    .setDuration(Duration.ofHours(1).toProtoDuration()),
            )
        }
        // Without a duration the rank is permanent, even for a player whose rank had an end.
        publish(forever.toString()) {
            setRank(RankChange.newBuilder().setPlayerUuid(forever.toString()).setPermissionGroupId(DEFAULT_GROUP))
        }
        publish(NETWORK) { setNetworkMaintenance(NetworkMaintenanceChange.newBuilder().setEnabled(true)) }
        runBlocking { eventually(WAIT) { network.load().value.maintenance shouldBe true } }
        publish(NETWORK) { setNetworkMaintenance(NetworkMaintenanceChange.newBuilder().setEnabled(false)) }

        runBlocking {
            eventually(WAIT) {
                network.load().value.maintenance shouldBe false
                groups.find(GroupId(lobby))?.value?.maintenance shouldBe true
                val expiresAt =
                    players
                        .find(player)
                        ?.rank
                        ?.expiresAt
                        .shouldNotBeNull()
                expiresAt.shouldBeBetween(sent.plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)))
                players.find(forever)?.rank?.expiresAt shouldBe null
                // Audited right after each change, so wait for the entries too.
                audit.about(admin.toString()).map { it.action } shouldContainExactlyInAnyOrder
                    listOf(
                        "PUT $GROUPS_PATH/{id}",
                        "PUT $RANK_PATH",
                        "PUT $RANK_PATH",
                        "PUT $NETWORK_PATH",
                        "PUT $NETWORK_PATH",
                    )
            }
        }
    }

    private fun publish(
        key: String,
        change: AdminChangeRequested.Builder.() -> Unit,
    ) = publisher.publishAndWait(
        KafkaTopics.ADMIN_CHANGE,
        key,
        AdminChangeRequested
            .newBuilder()
            .setMetadata(metadata.create(Actor(ActorType.PLAYER, admin.toString())))
            .apply(change)
            .build(),
    )

    private companion object {
        const val SUFFIX = 8
        const val DEFAULT_GROUP = "default"
        const val NETWORK = "network"
        val WAIT = 30.seconds
    }
}
