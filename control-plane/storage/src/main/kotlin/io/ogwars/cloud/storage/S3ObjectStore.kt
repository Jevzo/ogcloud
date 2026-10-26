package io.ogwars.cloud.storage

import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.StoredObject
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.time.Duration
import java.time.Instant

class S3ObjectStore(
    private val settings: S3Settings,
) : ObjectStore,
    AutoCloseable {
    private val credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(settings.accessKey, settings.secretKey),
        )

    private val s3: S3Client =
        S3Client
            .builder()
            .endpointOverride(settings.endpoint)
            .region(Region.of(settings.region))
            .credentialsProvider(credentials)
            .forcePathStyle(settings.pathStyle)
            // S3-compatible stores do not all support the SDK's newer default checksums.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build()

    private val presigner: S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(settings.endpoint)
            .region(Region.of(settings.region))
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(settings.pathStyle).build())
            .build()

    override fun upload(
        key: String,
        content: InputStream,
    ) {
        val first = content.readNBytes(settings.partSizeBytes)
        if (first.size < settings.partSizeBytes) {
            s3.putObject({ it.bucket(settings.bucket).key(key) }, RequestBody.fromBytes(first))
            return
        }
        uploadInParts(key, first, content)
    }

    // Holds one part in memory at a time, so archives of any size stream through.
    private fun uploadInParts(
        key: String,
        first: ByteArray,
        rest: InputStream,
    ) {
        val uploadId = s3.createMultipartUpload { it.bucket(settings.bucket).key(key) }.uploadId()
        try {
            val parts = mutableListOf<CompletedPart>()
            var chunk = first
            while (chunk.isNotEmpty()) {
                val number = parts.size + 1
                val etag =
                    s3
                        .uploadPart(
                            {
                                it
                                    .bucket(settings.bucket)
                                    .key(key)
                                    .uploadId(uploadId)
                                    .partNumber(number)
                            },
                            RequestBody.fromBytes(chunk),
                        ).eTag()
                parts +=
                    CompletedPart
                        .builder()
                        .partNumber(number)
                        .eTag(etag)
                        .build()
                chunk = rest.readNBytes(settings.partSizeBytes)
            }
            s3.completeMultipartUpload {
                it
                    .bucket(
                        settings.bucket,
                    ).key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
            }
        } catch (e: Exception) {
            val failure = StorageException("Upload of $key failed", e)
            // The same outage often fails the abort too; the original cause matters more, and the cleanup loop retries.
            runCatching { s3.abortMultipartUpload { it.bucket(settings.bucket).key(key).uploadId(uploadId) } }
                .onFailure(failure::addSuppressed)
            throw failure
        }
    }

    override fun download(
        key: String,
        target: OutputStream,
    ) {
        s3.getObject { it.bucket(settings.bucket).key(key) }.use { it.transferTo(target) }
    }

    override fun head(key: String): StoredObject? =
        try {
            val head = s3.headObject { it.bucket(settings.bucket).key(key) }
            StoredObject(key, head.contentLength(), head.lastModified())
        } catch (_: NoSuchKeyException) {
            null
        }

    override fun list(prefix: String): List<StoredObject> =
        s3
            .listObjectsV2Paginator { it.bucket(settings.bucket).prefix(prefix) }
            .contents()
            .map { StoredObject(it.key(), it.size(), it.lastModified()) }

    override fun delete(key: String) {
        s3.deleteObject { it.bucket(settings.bucket).key(key) }
    }

    override fun presignDownload(
        key: String,
        validFor: Duration,
    ): URI =
        presigner
            .presignGetObject {
                it.signatureDuration(validFor).getObjectRequest { r ->
                    r.bucket(settings.bucket).key(key)
                }
            }.url()
            .toURI()

    override fun presignUpload(
        key: String,
        validFor: Duration,
    ): URI =
        presigner
            .presignPutObject {
                it.signatureDuration(validFor).putObjectRequest { r ->
                    r.bucket(settings.bucket).key(key)
                }
            }.url()
            .toURI()

    override fun abortStaleUploads(cutoff: Instant): Int {
        val stale =
            s3
                .listMultipartUploadsPaginator { it.bucket(settings.bucket) }
                .uploads()
                .filter { it.initiated().isBefore(cutoff) }
        stale.forEach { upload ->
            s3.abortMultipartUpload { it.bucket(settings.bucket).key(upload.key()).uploadId(upload.uploadId()) }
        }
        return stale.size
    }

    override fun close() {
        presigner.close()
        s3.close()
    }
}

class StorageException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
