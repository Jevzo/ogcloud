package io.ogwars.cloud.controller.template

import io.ogwars.cloud.contracts.internal.v1.ActiveTemplate
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
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
    private val versions = TemplateVersions(f.servers, f.groupStore, store, readModels, { UUID.randomUUID() }, f.clock)

    @Test
    fun `a STATIC server's push becomes the group's active template`() {
        val server = f.request(smp)

        val registered = versions.register(server, "templates/smp/a.tar.gz", SHA, 1024)

        registered.promoted shouldBe true
        f.groupStore
            .find(smp.id)
            ?.value
            ?.activeTemplateVersionId shouldBe registered.id
        ActiveTemplate.parseFrom(readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "smp")).objectKey shouldBe
            "templates/smp/a.tar.gz"
    }

    @Test
    fun `a DYNAMIC push waits for an explicit promote`() {
        val server = f.request(bedwars)

        val registered = versions.register(server, "templates/bedwars/b.tar.gz", SHA, 2048)

        registered.promoted shouldBe false
        readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "bedwars") shouldBe null
        versions.promote(bedwars.id, registered.id)
        ActiveTemplate.parseFrom(readModels.get(ReadModelKind.ACTIVE_TEMPLATE, "bedwars")).templateVersionId shouldBe
            registered.id.toString()
    }

    @Test
    fun `uploads outside the group's prefix or from unknown servers are refused`() {
        val server = f.request(bedwars)

        shouldThrow<IllegalArgumentException> { versions.register(server, "templates/smp/x.tar.gz", SHA, 1) }
        shouldThrow<IllegalArgumentException> {
            versions.register(
                server,
                "templates/bedwars/x.tar.gz",
                "not-a-hash",
                1,
            )
        }
        shouldThrow<NoSuchElementException> {
            versions.register(
                UUID.randomUUID(),
                "templates/bedwars/x.tar.gz",
                SHA,
                1,
            )
        }
    }

    private companion object {
        val SHA = "a".repeat(64)
    }
}
