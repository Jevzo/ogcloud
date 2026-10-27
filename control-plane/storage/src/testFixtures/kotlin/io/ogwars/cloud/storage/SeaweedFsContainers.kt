package io.ogwars.cloud.storage

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

/** Same SeaweedFS as the chart (D-0010). */
const val SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.51"
const val SEAWEEDFS_S3_PORT = 8333
const val TEST_BUCKET = "ogcloud-test"
const val TEST_ACCESS_KEY = "ogcloud-access"
const val TEST_SECRET_KEY = "ogcloud-secret-key"
const val TEST_REGION = "us-east-1"

private const val IDENTITIES =
    """{"identities":[{"name":"ogcloud","credentials":""" +
        """[{"accessKey":"$TEST_ACCESS_KEY","secretKey":"$TEST_SECRET_KEY"}],""" +
        """"actions":["Admin","Read","Write","List","Tagging"]}]}"""

/** SeaweedFS with an S3 gateway that knows [TEST_ACCESS_KEY]; the caller starts it. */
fun seaweedFsContainer(): GenericContainer<*> =
    GenericContainer(SEAWEEDFS_IMAGE)
        .withCopyToContainer(Transferable.of(IDENTITIES), "/etc/seaweedfs/s3.json")
        .withCommand("server", "-dir=/data", "-s3", "-s3.port=$SEAWEEDFS_S3_PORT", "-s3.config=/etc/seaweedfs/s3.json")
        .withExposedPorts(SEAWEEDFS_S3_PORT)
        .waitingFor(Wait.forListeningPorts(SEAWEEDFS_S3_PORT))

fun GenericContainer<*>.s3Endpoint(): URI = URI("http://$host:${getMappedPort(SEAWEEDFS_S3_PORT)}")

/** An admin client for the started container, e.g. to create buckets. */
fun GenericContainer<*>.s3Admin(): S3Client =
    S3Client
        .builder()
        .endpointOverride(s3Endpoint())
        .region(Region.of(TEST_REGION))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(TEST_ACCESS_KEY, TEST_SECRET_KEY)),
        ).forcePathStyle(true)
        .build()

/** SeaweedFS with [TEST_BUCKET], wired into `ogcloud.s3.*` for Spring tests of services. */
@TestConfiguration(proxyBeanMethods = false)
class SeaweedFsContainerConfiguration {
    @Bean
    fun seaweedFs(): GenericContainer<*> =
        seaweedFsContainer()
            .also { container ->
                container.start()
            }.also(::createBucket)

    @Bean
    fun seaweedFsProperties(seaweedFs: GenericContainer<*>) =
        DynamicPropertyRegistrar { registry ->
            registry.add("ogcloud.s3.endpoint") { seaweedFs.s3Endpoint().toString() }
            registry.add("ogcloud.s3.region") { TEST_REGION }
            registry.add("ogcloud.s3.bucket") { TEST_BUCKET }
            registry.add("ogcloud.s3.access-key") { TEST_ACCESS_KEY }
            registry.add("ogcloud.s3.secret-key") { TEST_SECRET_KEY }
            registry.add("ogcloud.s3.path-style") { "true" }
        }

    private fun createBucket(container: GenericContainer<*>) {
        container.s3Admin().use { admin -> admin.createBucket { it.bucket(TEST_BUCKET) } }
    }
}
