package io.ogwars.cloud.images

import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.DockerClientFactory
import java.util.concurrent.TimeUnit

/**
 * The platform images start their service as uid 1000 (T-0509). Without configuration each one stops at once with
 * its own error, which proves the binary or the Spring layers run on the image; the full start is the chart's job.
 */
class PlatformImagesSmokeTest {
    private val docker: DockerClient = DockerClientFactory.instance().client()

    @ParameterizedTest
    @ValueSource(strings = ["gateway", "loadbalancer", "template-loader", "bootstrap"])
    fun `a Go service runs and reports its missing configuration`(binary: String) {
        val (exitCode, logs) = run(binary)

        exitCode shouldBe GO_CONFIG_ERROR
        logs shouldContain "$binary: "
    }

    @ParameterizedTest
    @ValueSource(strings = ["api", "controller"])
    fun `a Spring service boots its application`(service: String) {
        val (_, logs) = run(service)

        logs shouldContain "Starting ${service.replaceFirstChar(Char::uppercase)}Application"
    }

    @Test
    fun `the controller image carries OgCloud's plugins for the runtime bundles`() {
        val id = docker.createContainerCmd(imageOf("controller")).exec().id
        try {
            val entries =
                TarArchiveInputStream(docker.copyArchiveFromContainerCmd(id, PLUGINS).exec()).use { tar ->
                    generateSequence { tar.nextEntry }.map { it.name.substringAfterLast('/') }.toList()
                }
            entries shouldContainAll RuntimeProfiles.ALL.map { "ogcloud-${it.platform.name.lowercase()}.jar" }
        } finally {
            docker.removeContainerCmd(id).withForce(true).exec()
        }
    }

    /** Runs the image until it stops on its own or the timeout ends, and returns its exit code and output. */
    private fun run(name: String): Pair<Int?, String> {
        val id = docker.createContainerCmd(imageOf(name)).exec().id
        try {
            docker.startContainerCmd(id).exec()
            val exitCode =
                runCatching {
                    docker
                        .waitContainerCmd(
                            id,
                        ).start()
                        .awaitStatusCode(RUN_SECONDS, TimeUnit.SECONDS)
                }.getOrNull()
            return exitCode to logs(id)
        } finally {
            docker.removeContainerCmd(id).withForce(true).exec()
        }
    }

    private fun logs(id: String): String {
        val output = StringBuilder()
        docker
            .logContainerCmd(id)
            .withStdOut(true)
            .withStdErr(true)
            .exec(
                object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        output.append(String(frame.payload))
                    }
                },
            ).awaitCompletion(RUN_SECONDS, TimeUnit.SECONDS)
        return output.toString()
    }

    private fun imageOf(name: String) = "${property("registry")}/$name:${property("version")}"

    private companion object {
        const val GO_CONFIG_ERROR = 1
        const val RUN_SECONDS = 60L
        const val PLUGINS = "/opt/ogcloud/plugins"
    }
}
