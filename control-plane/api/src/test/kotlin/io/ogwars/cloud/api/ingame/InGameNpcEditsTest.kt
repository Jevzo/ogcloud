package io.ogwars.cloud.api.ingame

import io.ogwars.cloud.api.npcs.NPCS_PATH
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.contracts.internal.v1.NpcChangeRequested
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.livestate.toProto
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** NPC edits from in-game admins (D-0129). */
@ApiTest
class InGameNpcEditsTest(
    @Autowired private val groups: GroupStore,
    @Autowired private val npcs: NpcStore,
    @Autowired private val audit: AuditLogStore,
    @Autowired private val publisher: EventPublisher,
    @Autowired private val metadata: EventMetadataFactory,
) {
    private val admin = UUID.randomUUID()
    private val hub = newGroup()
    private val arena = newGroup()

    @Test
    fun `an in-game admin's edits land like dashboard edits, audited as the player`() {
        val id = UUID.randomUUID()

        publish(hub, id) { setCreate(npc(id, hub, "Shop")) }
        runBlocking { eventually(WAIT) { npcs.find(id)?.title shouldBe "Shop" } }
        publish(hub, id) { setUpdate(npc(id, hub, "Shop 2")) }
        runBlocking { eventually(WAIT) { npcs.find(id)?.title shouldBe "Shop 2" } }
        publish(hub, id) { setDeleteNpcId(id.toString()) }
        runBlocking {
            eventually(WAIT) {
                npcs.find(id) shouldBe null
                // Audited right after each change, so wait for the entries too.
                audit.about(admin.toString()).map { it.action } shouldContainExactlyInAnyOrder
                    listOf("POST $NPCS_PATH", "PUT $NPCS_PATH/{id}", "DELETE $NPCS_PATH/{id}")
            }
        }
    }

    @Test
    fun `a server edits only its own group's NPCs`() {
        val id = UUID.randomUUID()

        publish(hub, id) { setCreate(npc(id, hub, "Shop")) }
        publish(arena, id) { setUpdate(npc(id, arena, "Taken")) }
        publish(arena, id) { setDeleteNpcId(id.toString()) }
        // Same key, same partition: once this edit shows, the refused ones were handled before it.
        publish(hub, id) { setUpdate(npc(id, hub, "Last")) }

        runBlocking { eventually(WAIT) { npcs.find(id)?.title shouldBe "Last" } }
        npcs.find(id)?.groupId shouldBe GroupId(hub)
    }

    private fun publish(
        group: String,
        id: UUID,
        change: NpcChangeRequested.Builder.() -> Unit,
    ) = publisher.publishAndWait(
        KafkaTopics.NPC_CHANGE,
        id.toString(),
        NpcChangeRequested
            .newBuilder()
            .setMetadata(metadata.create(Actor(ActorType.PLAYER, admin.toString())))
            .setGroupId(group)
            .apply(change)
            .build(),
    )

    private fun npc(
        id: UUID,
        group: String,
        title: String,
    ) = Npc(id, GroupId(group), Location("world", 0.5, 64.0, 0.5), title).toProto()

    private fun newGroup(): String {
        val id = "npc-${UUID.randomUUID().toString().take(SUFFIX)}"
        groups.create(group(id = id))
        return id
    }

    private companion object {
        const val SUFFIX = 8
        val WAIT = 30.seconds
    }
}
