package io.ogwars.cloud.storage

import io.ogwars.cloud.domain.common.REDACTED
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("ogcloud.s3")
data class S3Settings(
    val endpoint: URI,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    /** SeaweedFS needs path-style URLs; Hetzner Object Storage accepts virtual-hosted ones. */
    val pathStyle: Boolean,
    /** Upload part size; S3 requires at least 5 MiB for every part but the last. */
    val partSizeBytes: Int = DEFAULT_PART_SIZE,
) {
    init {
        require(partSizeBytes >= MIN_PART_SIZE) { "partSizeBytes must be at least 5 MiB" }
    }

    override fun toString() =
        "S3Settings(endpoint=$endpoint, region=$region, bucket=$bucket, accessKey=$accessKey, secretKey=$REDACTED, " +
            "pathStyle=$pathStyle, partSizeBytes=$partSizeBytes)"

    companion object {
        const val MIN_PART_SIZE = 5 * 1024 * 1024
        const val DEFAULT_PART_SIZE = 16 * 1024 * 1024
    }
}
