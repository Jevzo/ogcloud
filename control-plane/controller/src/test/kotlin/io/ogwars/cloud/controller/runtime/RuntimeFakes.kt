package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import io.ogwars.cloud.domain.store.StoredObject
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.time.Duration
import java.time.Instant

/** Objects are stamped with [now] when uploaded, so tests can age them. */
class InMemoryObjectStore(
    var now: Instant = Instant.EPOCH,
) : ObjectStore {
    val objects = mutableMapOf<String, ByteArray>()
    private val modified = mutableMapOf<String, Instant>()

    override fun upload(
        key: String,
        content: InputStream,
    ) {
        objects[key] = content.readAllBytes()
        modified[key] = now
    }

    override fun download(
        key: String,
        target: OutputStream,
    ) {
        target.write(objects.getValue(key))
    }

    override fun head(key: String) = objects[key]?.let { StoredObject(key, it.size.toLong(), modified.getValue(key)) }

    override fun list(prefix: String) =
        objects
            .filterKeys {
                it.startsWith(prefix)
            }.map { (key, bytes) -> StoredObject(key, bytes.size.toLong(), modified.getValue(key)) }

    override fun delete(key: String) {
        objects.remove(key)
    }

    override fun presignDownload(
        key: String,
        validFor: Duration,
    ): URI = throw UnsupportedOperationException("not used by these tests")

    override fun presignUpload(
        key: String,
        validFor: Duration,
    ): URI = throw UnsupportedOperationException("not used by these tests")

    override fun abortStaleUploads(cutoff: Instant): Int = 0
}

class InMemoryRuntimeArtifactStore : RuntimeArtifactStore {
    private val artifacts = mutableMapOf<Pair<String, String>, RuntimeArtifact>()

    override fun findByProfile(profileId: String) = artifacts.values.filter { it.profileId == profileId }

    override fun save(artifact: RuntimeArtifact) {
        artifacts[artifact.profileId to artifact.path] = artifact
    }

    override fun delete(
        profileId: String,
        path: String,
    ) {
        artifacts.remove(profileId to path)
    }
}
