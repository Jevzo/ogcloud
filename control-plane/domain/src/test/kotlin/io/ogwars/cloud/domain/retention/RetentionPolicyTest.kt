package io.ogwars.cloud.domain.retention

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class RetentionPolicyTest {
    private val policy = RetentionPolicy()

    private fun version(
        n: Int,
        daysAgo: Long,
    ) = TemplateVersion(
        id = UUID(0, n.toLong()),
        groupId = GroupId("lobby"),
        objectKey = "templates/lobby/$n.tar.gz",
        sha256 = "0".repeat(64),
        sizeBytes = 1,
        source = TemplateSource.PUSH,
        createdBy = Actor.system("test"),
        createdAt = T0.minus(Duration.ofDays(daysAgo)),
    )

    @Test
    fun `keeps the active version plus the three newest others`() {
        val versions = (1..6).map { version(it, daysAgo = it.toLong()) }
        val active = versions.last()

        policy.templateVersionsToDelete(versions, active.id) shouldContainExactly listOf(versions[3], versions[4])
    }

    @Test
    fun `nothing is deleted while there are few versions`() {
        policy.templateVersionsToDelete(listOf(version(1, 1), version(2, 2)), activeVersionId = null) shouldBe
            emptyList()
    }

    @Test
    fun `cutoffs follow the configured ages`() {
        policy.cutoff(RetentionKind.AUDIT_LOG, T0) shouldBe T0.minus(Duration.ofDays(90))
        policy.cutoff(RetentionKind.SCALING_LOG, T0) shouldBe T0.minus(Duration.ofDays(30))
    }

    @Test
    fun `deleted groups are purged after the seven day grace`() {
        policy.isGroupPurgeDue(T0, T0.plus(Duration.ofDays(7)).minusSeconds(1)) shouldBe false
        policy.isGroupPurgeDue(T0, T0.plus(Duration.ofDays(7))) shouldBe true
    }
}
