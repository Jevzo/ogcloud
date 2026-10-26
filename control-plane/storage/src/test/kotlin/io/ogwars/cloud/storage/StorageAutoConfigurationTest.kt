package io.ogwars.cloud.storage

import io.ogwars.cloud.domain.store.ObjectStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.net.URI

class StorageAutoConfigurationTest {
    @Test
    fun `binds the S3 settings and exposes the object store`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration::class.java))
            .withPropertyValues(
                "ogcloud.s3.endpoint=http://seaweedfs:8333",
                "ogcloud.s3.region=eu-central",
                "ogcloud.s3.bucket=ogcloud",
                "ogcloud.s3.access-key=access",
                "ogcloud.s3.secret-key=secret",
                "ogcloud.s3.path-style=true",
            ).run { context ->
                context.getBean(S3Settings::class.java).endpoint shouldBe URI("http://seaweedfs:8333")
                context.getBean(ObjectStore::class.java)
            }
    }
}
