package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.group.scaling
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.server.ServerState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class RestartJobsTest {
    private val bedwars = group(id = "bedwars", maintenance = true)

    @Test
    fun `a group restart replaces every server in batches`() {
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars))
        val old = List(3) { j.running(bedwars) }
        val job = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars", JobKinds.BATCH_SIZE to "2"))

        val done = j.runToEnd(j.runner(*j.restartHandlers()), job)

        done.state shouldBe JobState.SUCCEEDED
        val now =
            j.f.servers
                .listByGroup(bedwars.id)
                .map { it.value }
        now.map { it.state }.distinct() shouldBe listOf(ServerState.RUNNING)
        now.size shouldBe 3
        now.map { it.id } shouldNotContainAnyOf old
        j.progress.last().state shouldBe JobState.SUCCEEDED
    }

    @Test
    fun `a new leader resumes the restart without repeating finished steps`() {
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars))
        repeat(4) { j.running(bedwars) }
        val job = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars", JobKinds.BATCH_SIZE to "2"))
        val oldLeader = j.runner(*j.restartHandlers())
        repeat(3) {
            oldLeader.runOnce()
            j.settle()
        }

        j.runToEnd(j.runner(*j.restartHandlers()), job).state shouldBe JobState.SUCCEEDED

        j.f.events.transitions
            .count { it.second == ServerState.REQUESTED } shouldBe 8
    }

    @Test
    fun `a group outside maintenance is not restarted`() {
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars.copy(maintenance = false)))
        val job = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars"))

        val done = j.runToEnd(j.runner(*j.restartHandlers()), job)

        done.state shouldBe JobState.FAILED
        done.message shouldContain "maintenance"
    }

    @Test
    fun `a STATIC server is stopped before its replacement starts`() {
        val smp = group(GroupType.STATIC, id = "smp", maintenance = true)
        val j = JobsFixture(listOf(group(id = "lobby"), smp))
        j.running(smp)
        val job = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "smp"))

        j.runToEnd(j.runner(*j.restartHandlers()), job).state shouldBe JobState.SUCCEEDED

        val order =
            j.f.events.transitions.map { it.second }.filter {
                it == ServerState.STOPPED ||
                    it == ServerState.REQUESTED
            }
        order shouldBe listOf(ServerState.REQUESTED, ServerState.STOPPED, ServerState.REQUESTED)
    }

    @Test
    fun `a replacement that crashes fails the job`() {
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars))
        val old = j.running(bedwars)
        val job = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars"))
        val replacements = {
            j.f.servers
                .listByGroup(bedwars.id)
                .map { it.value.id }
                .filter { it != old }
                .toSet()
        }

        val done = j.runToEnd(j.runner(*j.restartHandlers()), job, failing = replacements)

        done.state shouldBe JobState.FAILED
        j.f.servers
            .get(old)
            ?.value
            ?.state shouldBe ServerState.RUNNING
    }

    @Test
    fun `a network restart goes proxies first, then the default group, then the rest`() {
        val proxy = group(GroupType.PROXY, id = "proxy")
        val lobby = group(id = "lobby", scaling = scaling(min = 1))
        val j = JobsFixture(listOf(proxy, lobby, bedwars), maintenance = true)
        listOf(bedwars, lobby, proxy).forEach { j.running(it) }
        j.f.events.requestedGroups
            .clear()
        val job = j.create(JobKinds.NETWORK_RESTART)

        j.runToEnd(j.runner(*j.restartHandlers()), job).state shouldBe JobState.SUCCEEDED

        j.f.events.requestedGroups
            .map { it.value } shouldContainExactly listOf("proxy", "lobby", "bedwars")
    }

    @Test
    fun `only one restart runs at a time`() {
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars))
        j.running(bedwars)
        val first = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars"))
        val second = j.create(JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to "bedwars"))
        val runner = j.runner(*j.restartHandlers())

        runner.runOnce()

        j.jobs.find(first)?.state shouldBe JobState.RUNNING
        j.jobs.find(second)?.state shouldBe JobState.QUEUED
    }
}
