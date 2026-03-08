package io.ogwars.cloud.controller.service

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TemplateService(
    private val minioClient: MinioClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun templateExists(
        bucket: String,
        path: String,
    ): Boolean {
        val objectPath = templateObjectPath(path)
        if (!bucketExists(bucket)) {
            log.warn("Bucket does not exist: {}", bucket)
            return false
        }

        return try {
            minioClient.statObject(
                StatObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(objectPath)
                    .build(),
            )
            true
        } catch (_: Exception) {
            log.warn("Template not found: bucket={}, path={}", bucket, objectPath)
            false
        }
    }

    private fun bucketExists(bucket: String): Boolean =
        minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())

    private fun templateObjectPath(path: String): String = "$path/$TEMPLATE_FILE_NAME"

    companion object {
        private const val TEMPLATE_FILE_NAME = "template.tar.gz"
    }
}
