package io.ogwars.cloud.domain.template

import io.ogwars.cloud.domain.group.GroupId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class TemplateKeysTest {
    private val lobby = GroupId("lobby")
    private val id = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc")

    @Test
    fun `keys belong to exactly one group`() {
        val key = TemplateKeys.of(lobby, id)

        key shouldBe "templates/lobby/$id.tar.gz"
        TemplateKeys.belongsTo(lobby, key) shouldBe true
        TemplateKeys.belongsTo(GroupId("lobby2"), key) shouldBe false
        TemplateKeys.belongsTo(lobby, "templates/lobby/../bedwars/$id.tar.gz") shouldBe false
        TemplateKeys.belongsTo(lobby, "templates/lobby/$id.zip") shouldBe false
    }
}
