package io.ogwars.cloud.api.events

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.AlertRaised
import io.ogwars.cloud.contracts.events.v1.AlertSeverity
import io.ogwars.cloud.contracts.events.v1.GroupType
import io.ogwars.cloud.contracts.events.v1.JobProgress
import io.ogwars.cloud.contracts.events.v1.ScalingAction
import io.ogwars.cloud.contracts.events.v1.ScalingDecided
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.UncheckedIOException
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/** Live updates over SSE with single-use tickets (D-0105). */
@ApiTest
class LiveEventsTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val publisher: EventPublisher,
    @Autowired private val metadata: EventMetadataFactory,
    @Autowired private val audit: AuditLogStore,
    @Autowired private val jobs: JobStore,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)

    @Test
    fun `a ticket opens the stream once and is not audited`() {
        val token = testUsers.token(testUsers.create())
        val ticket = ticket(token)

        oauth.events(ticket).use { first ->
            first.statusCode() shouldBe OK
            first.headers().firstValue("Content-Type").orElse("") shouldStartWith "text/event-stream"
            oauth.events(ticket).use { it.statusCode() shouldBe UNAUTHORIZED }
        }
        oauth.events("not-a-ticket").use { it.statusCode() shouldBe UNAUTHORIZED }
        oauth.call("POST", TICKETS, token = null).statusCode() shouldBe UNAUTHORIZED
        audit.list(before = null, limit = AUDIT_PAGE).map { it.action } shouldNotContain "POST $TICKETS"
    }

    @Test
    fun `events reach only callers holding their permission`() {
        val serversOnly = testUsers.token(testUsers.create(), listOf(ApiPermission.SERVERS_READ))
        val both = testUsers.token(testUsers.create(), listOf(ApiPermission.SERVERS_READ, ApiPermission.OVERVIEW_READ))
        // Events are queued in the order streams connected, so the first stream gets each event before the second.
        EventStream(oauth.events(ticket(serversOnly))).use { first ->
            EventStream(oauth.events(ticket(both))).use { second ->
                val group = "sse-${UUID.randomUUID().toString().take(SUFFIX)}"
                second.await("scaling", group) { publishScaling(group) }

                val serverId = UUID.randomUUID().toString()
                val server = first.await("server", serverId) { publishLifecycle(serverId) }

                server shouldContain "\"state\":\"RUNNING\""
                server shouldContain "\"previousState\":\"STARTING\""
                first.names() shouldNotContain "scaling"
            }
        }
    }

    @Test
    fun `job events follow the area that started the job, alerts need the overview`() {
        val networkOnly = testUsers.token(testUsers.create(), listOf(ApiPermission.NETWORK_READ))
        val groupsAndOverview =
            testUsers.token(testUsers.create(), listOf(ApiPermission.GROUPS_READ, ApiPermission.OVERVIEW_READ))
        val groupJob = job(JobKinds.GROUP_RESTART)
        val networkJob = job(JobKinds.NETWORK_RESTART)
        EventStream(oauth.events(ticket(networkOnly))).use { first ->
            EventStream(oauth.events(ticket(groupsAndOverview))).use { second ->
                second.await("job", groupJob.toString()) { publishProgress(groupJob) }
                val subject = "alert-${UUID.randomUUID()}"
                second.await("alert", subject) { publishAlert(subject) }

                first.await("job", networkJob.toString()) { publishProgress(networkJob) }

                first.names() shouldNotContain "alert"
                first.data().none { groupJob.toString() in it } shouldBe true
            }
        }
    }

    private fun job(kind: String): UUID {
        val id = UUID.randomUUID()
        jobs.create(
            Job(id, kind, JobState.RUNNING, emptyMap(), emptyMap(), 0, 1, "", Actor.system("test"), Instant.now()),
        )
        return id
    }

    private fun publishProgress(jobId: UUID) =
        publisher.publishAndWait(
            KafkaTopics.JOB_PROGRESS,
            jobId.toString(),
            JobProgress
                .newBuilder()
                .setMetadata(metadata.create(Actor.system("test")))
                .setJobId(jobId.toString())
                .setState(io.ogwars.cloud.contracts.events.v1.JobState.JOB_STATE_RUNNING)
                .setTotalSteps(1)
                .build(),
        )

    private fun publishAlert(subject: String) =
        publisher.publishAndWait(
            KafkaTopics.ALERT_RAISED,
            subject,
            AlertRaised
                .newBuilder()
                .setMetadata(metadata.create(Actor.system("test")))
                .setSubjectId(subject)
                .setSeverity(AlertSeverity.ALERT_SEVERITY_WARNING)
                .setKind("group.crash-loop-paused")
                .build(),
        )

    private fun ticket(token: String): String = oauth.json(oauth.call("POST", TICKETS, token))["ticket"] as String

    private fun publishLifecycle(serverId: String) =
        publisher.publishAndWait(
            KafkaTopics.SERVER_LIFECYCLE,
            serverId,
            ServerLifecycleChanged
                .newBuilder()
                .setMetadata(metadata.create(Actor.system("test")))
                .setServerId(serverId)
                .setGroupId("lobby")
                .setGroupType(GroupType.GROUP_TYPE_DYNAMIC)
                .setState(ServerState.SERVER_STATE_RUNNING)
                .setPreviousState(ServerState.SERVER_STATE_STARTING)
                .build(),
        )

    private fun publishScaling(group: String) =
        publisher.publishAndWait(
            KafkaTopics.SCALING_DECIDED,
            group,
            ScalingDecided
                .newBuilder()
                .setMetadata(metadata.create(Actor.system("test")))
                .setGroupId(group)
                .setAction(ScalingAction.SCALING_ACTION_START)
                .setReason("test")
                .build(),
        )

    /** Reads `event:`/`data:` pairs off an open stream. */
    private class EventStream(
        private val response: HttpResponse<Stream<String>>,
    ) : AutoCloseable {
        private val received = LinkedBlockingQueue<Pair<String, String>>()
        private val seen = CopyOnWriteArrayList<Pair<String, String>>()

        init {
            response.statusCode() shouldBe OK
            Thread.ofVirtual().start {
                var name = ""
                try {
                    response.body().forEach { line ->
                        when {
                            line.startsWith(EVENT) -> {
                                name = line.removePrefix(EVENT)
                            }

                            line.startsWith(
                                DATA,
                            ) -> {
                                (name to line.removePrefix(DATA)).let {
                                    received.add(it)
                                    seen.add(it)
                                }
                            }
                        }
                    }
                } catch (_: UncheckedIOException) {
                    // The test closed the stream.
                }
            }
        }

        fun names(): List<String> = seen.map { it.first }

        fun data(): List<String> = seen.map { it.second }

        /**
         * The broadcast consumers start at the latest offset once Kafka assigns them, so [publish] repeats until the
         * event with [marker] arrives.
         */
        fun await(
            name: String,
            marker: String,
            publish: () -> Unit,
        ): String {
            val deadline = System.nanoTime() + WAIT.toNanos()
            while (System.nanoTime() < deadline) {
                publish()
                val match =
                    generateSequence { received.poll(RETRY.toMillis(), TimeUnit.MILLISECONDS) }
                        .firstOrNull { it.first == name && marker in it.second }
                if (match != null) return match.second
            }
            error("No $name event with $marker within $WAIT")
        }

        override fun close() = response.body().close()
    }

    private fun HttpResponse<Stream<String>>.use(block: (HttpResponse<Stream<String>>) -> Unit) =
        try {
            block(this)
        } finally {
            body().close()
        }

    private companion object {
        const val TICKETS = "/api/v1/events/tickets"
        const val EVENT = "event:"
        const val DATA = "data:"
        const val SUFFIX = 8
        const val AUDIT_PAGE = 100
        const val OK = 200
        const val UNAUTHORIZED = 401
        val WAIT: Duration = Duration.ofSeconds(30)
        val RETRY: Duration = Duration.ofMillis(500)
    }
}
