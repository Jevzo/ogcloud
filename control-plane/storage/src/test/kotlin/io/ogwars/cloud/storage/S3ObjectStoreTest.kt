package io.ogwars.cloud.storage

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** Same SeaweedFS as the chart (D-0010). */
const val SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.51"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ObjectStoreTest {
    private val identities =
        """{"identities":[{"name":"ogcloud","credentials":[{"accessKey":"$ACCESS","secretKey":"$SECRET"}],""" +
            """"actions":["Admin","Read","Write","List","Tagging"]}]}"""

    private val seaweed: GenericContainer<*> =
        GenericContainer(SEAWEEDFS_IMAGE)
            .withCopyToContainer(Transferable.of(identities), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=$S3_PORT", "-s3.config=/etc/seaweedfs/s3.json")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forListeningPorts(S3_PORT))
            .also { it.start() }

    private val endpoint = URI("http://${seaweed.host}:${seaweed.getMappedPort(S3_PORT)}")
    private val admin =
        S3Client
            .builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS, SECRET)))
            .forcePathStyle(true)
            .build()
            .also { client -> client.createBucket { it.bucket(BUCKET) } }

    private val store = S3ObjectStore(settings(SECRET))
    private val http = HttpClient.newHttpClient()

    @AfterAll
    fun stop() {
        store.close()
        admin.close()
        seaweed.stop()
    }

    @Test
    fun `small objects round-trip and can be listed, inspected and deleted`() {
        store.upload("templates/lobby/1.tar.gz", "hello".byteInputStream())

        download("templates/lobby/1.tar.gz") shouldBe "hello".toByteArray().toList()
        store.head("templates/lobby/1.tar.gz")?.sizeBytes shouldBe 5
        store.list("templates/lobby/").map { it.key } shouldContainExactly listOf("templates/lobby/1.tar.gz")

        store.delete("templates/lobby/1.tar.gz")
        store.head("templates/lobby/1.tar.gz") shouldBe null
    }

    @Test
    fun `large objects are uploaded in parts`() {
        val data = Random(42).nextBytes(S3Settings.MIN_PART_SIZE * 2 + 1234)

        store.upload("templates/smp/1.tar.gz", ByteArrayInputStream(data))

        download("templates/smp/1.tar.gz") shouldBe data.toList()
    }

    @Test
    fun `presigned links work without credentials`() {
        val upload = store.presignUpload("runtime/velocity/ViaVersion.jar", Duration.ofMinutes(5))
        http
            .send(
                HttpRequest.newBuilder(upload).PUT(HttpRequest.BodyPublishers.ofString("jar")).build(),
                HttpResponse.BodyHandlers.discarding(),
            ).statusCode() shouldBe 200

        val download = store.presignDownload("runtime/velocity/ViaVersion.jar", Duration.ofMinutes(5))
        http.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofString()).body() shouldBe
            "jar"
    }

    @Test
    fun `stale multipart uploads are aborted`() {
        admin.createMultipartUpload { it.bucket(BUCKET).key("templates/abandoned.tar.gz") }.uploadId().shouldNotBeNull()

        store.abortStaleUploads(Instant.now().plus(Duration.ofMinutes(1))) shouldBe 1
        store.abortStaleUploads(Instant.now().plus(Duration.ofMinutes(1))) shouldBe 0
    }

    @Test
    fun `wrong credentials are rejected`() {
        S3ObjectStore(settings("wrong")).use { intruder ->
            shouldThrowAny { intruder.upload("x", "y".byteInputStream()) }
        }
    }

    private fun settings(secret: String) =
        S3Settings(
            endpoint,
            "us-east-1",
            BUCKET,
            ACCESS,
            secret,
            pathStyle = true,
            partSizeBytes = S3Settings.MIN_PART_SIZE,
        )

    private fun download(key: String): List<Byte> =
        ByteArrayOutputStream()
            .also {
                store.download(key, it)
            }.toByteArray()
            .toList()

    private companion object {
        const val S3_PORT = 8333
        const val BUCKET = "ogcloud-test"
        const val ACCESS = "ogcloud-access"
        const val SECRET = "ogcloud-secret-key"
    }
}
