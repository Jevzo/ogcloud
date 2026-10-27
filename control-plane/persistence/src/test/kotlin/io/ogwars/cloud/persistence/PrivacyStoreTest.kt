package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.PseudonymStore
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** What an erasure touches in PostgreSQL (D-0049, D-0107). */
@PersistenceTest
class PrivacyStoreTest(
    @Autowired private val audit: AuditLogStore,
    @Autowired private val pseudonyms: PseudonymStore,
    @Autowired private val jobs: JobStore,
    @Autowired private val groups: GroupStore,
    @Autowired private val templates: TemplateVersionStore,
    @Autowired private val players: PlayerStore,
    @Autowired private val users: WebUserStore,
    @Autowired private val jdbc: JdbcClient,
) {
    private val person = UUID(0x0192d2c1_0000_7000L, 0x42).toString()
    private val user = Actor(ActorType.USER, person)

    @Test
    fun `entries a person caused or was the target of are found, others are not`() {
        val caused = entry(UUID(0x0192d2c1_0000_7000L, 1), user, "lobby")
        val about = entry(UUID(0x0192d2c1_0000_7000L, 2), Actor.system("test"), person)
        val unrelated = entry(UUID(0x0192d2c1_0000_7000L, 3), Actor(ActorType.CLIENT, person), "lobby")
        listOf(caused, about, unrelated).forEach(audit::append)

        audit.about(person).map { it.id } shouldContainExactly listOf(about.id, caused.id)
    }

    @Test
    fun `pseudonymising leaves no trace of the person in audit entries, jobs or templates`() {
        audit.append(entry(UUID(0x0192d2c1_0000_7000L, 4), user, person))
        jobs.create(job())
        groups.create(group(id = "privacy"))
        templates.add(
            TemplateVersion(
                UUID.randomUUID(),
                group(id = "privacy").id,
                "templates/privacy/1.tar.gz",
                "a".repeat(64),
                1,
                TemplateSource.UPLOAD,
                user,
                NOW,
            ),
        )

        val changed = pseudonyms.pseudonymise(person, "erased-1")

        changed shouldBe 4
        for (table in listOf("audit_log", "jobs", "template_versions")) {
            jdbc
                .sql(
                    "select count(*) from $table t where t::text like :id",
                ).param("id", "%$person%")
                .query(Long::class.java)
                .single() shouldBe
                0
        }
        audit.about("erased-1").single().actor shouldBe Actor(ActorType.USER, "erased-1")
    }

    @Test
    fun `deleting a player clears the link of the user who held it`() {
        val uuid = UUID.randomUUID()
        players.recordSeen(uuid, "Erased", NOW, PlayerRank(uuid, "default", null))
        val linked =
            WebUser(
                UUID.randomUUID(),
                "linked-${uuid.toString().take(SUFFIX)}",
                "$uuid@example.com",
                null,
                "viewer",
                NOW,
                uuid,
            )
        users.create(linked)

        players.delete(uuid) shouldBe true

        players.find(uuid) shouldBe null
        users.find(linked.id)?.value?.linkedPlayerUuid shouldBe null
        players.delete(uuid) shouldBe false
    }

    private fun entry(
        id: UUID,
        actor: Actor,
        target: String,
    ) = AuditEntry(id, NOW, actor, "POST /x", "x", target)

    private fun job() =
        Job(
            id = UUID.randomUUID(),
            kind = "group-restart",
            state = JobState.QUEUED,
            payload = emptyMap(),
            progress = emptyMap(),
            completedSteps = 0,
            totalSteps = 1,
            message = "",
            createdBy = user,
            createdAt = NOW,
        )

    private companion object {
        const val SUFFIX = 8
    }
}
