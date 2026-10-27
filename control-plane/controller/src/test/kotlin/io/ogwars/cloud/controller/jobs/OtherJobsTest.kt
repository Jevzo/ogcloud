package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.server.ServerState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory

class OtherJobsTest {
    @Test
    fun `a group delete stops the group's servers and ends when none are left`() {
        val bedwars = group(id = "bedwars")
        val j = JobsFixture(listOf(group(id = "lobby"), bedwars))
        repeat(2) { j.running(bedwars, players = 3) }
        val job = j.create(JobKinds.GROUP_DELETE, mapOf(JobKinds.GROUP_ID to "bedwars"))

        j.runToEnd(j.runner(GroupDeleteJob(j.f.servers, j.f.lifecycle)), job).state shouldBe JobState.SUCCEEDED

        j.f.servers.listByGroup(bedwars.id) shouldBe emptyList()
    }

    @Test
    fun `a rotation adds a token, rolls the proxies, then removes the old token`() {
        val proxy = group(GroupType.PROXY, id = "proxy")
        val j = JobsFixture(listOf(group(id = "lobby"), proxy))
        val oldProxy = j.running(proxy)
        val infisical = FakeTokenSource(mutableListOf("old-token"))
        val job = j.create(JobKinds.FORWARDING_ROTATE)
        val rotation =
            ForwardingRotationJob(provider(infisical), { infisical.tokens() }, { "new-token" }, j.f.groupStore, j.roll)

        val done = j.runToEnd(j.runner(rotation), job)

        done.state shouldBe JobState.SUCCEEDED
        infisical.tokens() shouldContainExactly listOf("new-token")
        j.f.servers.get(oldProxy) shouldBe null
        j.f.servers
            .listByGroup(proxy.id)
            .map { it.value.state } shouldBe listOf(ServerState.RUNNING)
        done.progress.values.none { "token" in it } shouldBe true
    }

    @Test
    fun `a rotation resumed after adding the token adopts it instead of adding another`() {
        val j = JobsFixture(listOf(group(id = "lobby")))
        val infisical = FakeTokenSource(mutableListOf("old-token"))
        var generated = 0
        val rotation =
            ForwardingRotationJob(
                provider(infisical),
                { infisical.tokens() },
                { "new-${++generated}" },
                j.f.groupStore,
                j.roll,
            )
        val job = j.create(JobKinds.FORWARDING_ROTATE)
        val runner = j.runner(rotation)
        runner.runOnce()
        infisical.add("new-0")

        j.runToEnd(runner, job).state shouldBe JobState.SUCCEEDED

        generated shouldBe 0
        infisical.tokens() shouldContainExactly listOf("new-0")
    }

    @Test
    fun `without a token source a rotation fails with a reason`() {
        val j = JobsFixture(listOf(group(id = "lobby")))
        val job = j.create(JobKinds.FORWARDING_ROTATE)
        val rotation =
            ForwardingRotationJob(StaticListableBeanFactory().getBeanProvider(ForwardingTokenSource::class.java), {
                emptyList()
            }, { "x" }, j.f.groupStore, j.roll)

        val done = j.runToEnd(j.runner(rotation), job)

        done.state shouldBe JobState.FAILED
        done.message shouldContain "Infisical"
    }

    @Test
    fun `an unknown job kind fails`() {
        val j = JobsFixture(listOf(group(id = "lobby")))
        val job = j.create("unknown.kind")

        j.runToEnd(j.runner(), job).message shouldContain "Unknown job kind"
    }

    private fun provider(source: ForwardingTokenSource) =
        StaticListableBeanFactory(mapOf("source" to source)).getBeanProvider(ForwardingTokenSource::class.java)

    private class FakeTokenSource(
        private val tokens: MutableList<String>,
    ) : ForwardingTokenSource {
        override fun tokens(): List<String> = tokens.toList()

        override fun add(token: String) {
            tokens += token
        }

        override fun remove(token: String) {
            tokens -= token
        }
    }
}
