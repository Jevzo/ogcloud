package io.ogwars.cloud.storage

import io.ogwars.cloud.domain.store.ObjectStore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** The [ObjectStore] port on the configured S3 endpoint (D-0074). */
@AutoConfiguration
@EnableConfigurationProperties(S3Settings::class)
class StorageAutoConfiguration {
    @Bean(destroyMethod = "close")
    fun objectStore(settings: S3Settings): ObjectStore = S3ObjectStore(settings)
}
