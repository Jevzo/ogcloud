package io.ogwars.cloud.controller.template

import io.ogwars.cloud.contracts.internal.v1.ActiveTemplate
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.lifecycle.PodEnd
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class TemplateVersionsTest {
    private val smp = group(GroupType.STATIC, id = "smp")
    private val bedwars = group(id = "bedwars")
    private val f = LifecycleFixture(listOf(smp, bedwars))
    private val store = InMemoryTemplateVersionStore()
    private val readModels = ValkeyReadModelStore(TestValkey.connection)
    private val versions =
        TemplateVersions(f.servers, f.groupStore, store, readModels, f.pods, { UUID.randomUUID() }, f.clock)

    @Test
    fun `a STATIC server's push becomes the group's active template`() {
        val server = f.request(smp)
        val key = key("smp")

        val registered = versions.register(server, key, SHA, 1024)

        registered.promoted shouldBe true
        f.groupStore
            .find(smp.id)
            ?.value
            ?.activeTemplateVersionId shouldBe registered.id
        ActiveTemplate.parseFrom(readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "smp")).objectKey shouldBe key
    }

    @Test
    fun `a push on shutdown counts after the server's record ended, as long as its pod exists`() {
        val server = f.request(smp)
        f.lifecycle.podEnded(server, PodEnd.STOPPED, "server container exited")
        f.pods.podGroups[server] = smp.id

        versions.register(server, key("smp"), SHA, 1024).promoted shouldBe true
    }

    @Test
    fun `a retried registration returns the first version`() {
        val server = f.request(bedwars)
        val key = key("bedwars")

        val first = versions.register(server, key, SHA, 1)

        versions.register(server, key, SHA, 1).id shouldBe first.id
        store.findByGroup(bedwars.id).size shouldBe 1
    }

    @Test
    fun `a DYNAMIC push waits for an explicit promote`() {
        val server = f.request(bedwars)

        val registered = versions.register(server, key("bedwars"), SHA, 2048)

        registered.promoted shouldBe false
        readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "bedwars") shouldBe null
        versions.promote(bedwars.id, registered.id)
        ActiveTemplate.parseFrom(readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "bedwars")).templateVersionId shouldBe
            registered.id.toString()
    }

    @Test
    fun `uploads outside the group's names, empty ones and unknown servers are refused`() {
        val server = f.request(bedwars)

        shouldThrow<IllegalArgumentException> { versions.register(server, key("smp"), SHA, 1) }
        shouldThrow<IllegalArgumentException> {
            versions.register(server, "templates/bedwars/../smp/${UUID.randomUUID()}.tar.gz", SHA, 1)
        }
        shouldThrow<IllegalArgumentException> { versions.register(server, key("bedwars"), SHA, 0) }
        shouldThrow<IllegalArgumentException> { versions.register(server, key("bedwars"), "not-a-hash", 1) }
        shouldThrow<NoSuchElementException> { versions.register(UUID.randomUUID(), key("bedwars"), SHA, 1) }
    }

    private fun key(group: String) = "templates/$group/${UUID.randomUUID()}.tar.gz"

    private companion object {
        val SHA = "a".repeat(64)
    }
}
