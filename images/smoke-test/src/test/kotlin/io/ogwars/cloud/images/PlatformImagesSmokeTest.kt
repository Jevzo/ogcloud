package io.ogwars.cloud.images

import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import io.kotest.matchers.collections.shouldContain
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
 * The platform images start their service (T-0509). Without configuration each one stops with its own error, which
 * proves the binary, or the Spring context up to its database, runs on the image; the full start is the chart's job.
 */
class PlatformImagesSmokeTest {
    private val docker: DockerClient = DockerClientFactory.instance().client()

    @ParameterizedTest
    @ValueSource(strings = ["gateway", "loadbalancer", "template-loader", "bootstrap", "backup"])
    fun `a Go service runs and reports its missing configuration`(binary: String) {
        val (exitCode, logs) = run(binary)

        exitCode shouldBe GO_CONFIG_ERROR
        logs shouldContain "$binary: "
    }

    @ParameterizedTest
    @ValueSource(strings = ["api", "controller"])
    fun `a Spring service wires its context up to the missing database`(service: String) {
        val (exitCode, logs) = run(service)

        exitCode shouldBe SPRING_START_FAILED
        logs shouldContain "Starting ${service.replaceFirstChar(Char::uppercase)}Application"
        logs shouldContain "Failed to configure a DataSource"
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["api", "controller", "gateway", "loadbalancer", "template-loader", "bootstrap", "backup", "web"],
    )
    fun `every platform image runs as uid 1000`(image: String) {
        docker
            .inspectImageCmd(imageOf(image))
            .exec()
            .config
            ?.user shouldBe "1000:1000"
    }

    @Test
    fun `the controller image carries OgCloud's plugins for the runtime bundles`() {
        filesIn("controller", PLUGINS) shouldContainAll
            RuntimeProfiles.ALL.map { "ogcloud-${it.platform.name.lowercase()}.jar" }
    }

    @Test
    fun `the API image carries the login pages it serves`() {
        filesIn("api", LOGIN_PAGES) shouldContain "index.html"
    }

    /** The names of the files under [path] in [image], read from a container that never starts. */
    private fun filesIn(
        image: String,
        path: String,
    ): List<String> {
        val id = docker.createContainerCmd(imageOf(image)).exec().id
        try {
            return TarArchiveInputStream(docker.copyArchiveFromContainerCmd(id, path).exec()).use { tar ->
                generateSequence { tar.nextEntry }.map { it.name.substringAfterLast('/') }.toList()
            }
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
        const val SPRING_START_FAILED = 1
        const val RUN_SECONDS = 60L
        const val PLUGINS = "/opt/ogcloud/plugins"

        // Where the API image keeps the built login pages (D-0212).
        const val LOGIN_PAGES = "/app/login"
    }
}
