package io.ogwars.cloud.domain.store

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.time.Duration
import java.time.Instant

data class StoredObject(
    val key: String,
    val sizeBytes: Long,
    val lastModified: Instant,
)

/** Templates and runtime bundles in S3-compatible storage (D-0010); the storage module implements it. */
interface ObjectStore {
    /** Streams [content] of unknown length into [key]; large inputs are uploaded in parts. */
    fun upload(
        key: String,
        content: InputStream,
    )

    fun download(
        key: String,
        target: OutputStream,
    )

    fun head(key: String): StoredObject?

    fun list(prefix: String): List<StoredObject>

    fun delete(key: String)

    /** Short-lived download link for pods, which hold no storage credentials. */
    fun presignDownload(
        key: String,
        validFor: Duration,
    ): URI

    fun presignUpload(
        key: String,
        validFor: Duration,
    ): URI

    /** Aborts multipart uploads started before [cutoff]; returns how many (D-0043). */
    fun abortStaleUploads(cutoff: Instant): Int
}
