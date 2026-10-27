package io.ogwars.cloud.api.templates

import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateKeys
import io.ogwars.cloud.domain.template.TemplatePromotion
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.util.unit.DataSize
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.UUID

/** `ogcloud.templates.*` */
@ConfigurationProperties("ogcloud.templates")
data class TemplateProperties(
    /** Uploads above this are refused (D-0087, D-0103). */
    val maxUploadSize: DataSize = DataSize.ofGigabytes(DEFAULT_MAX_GIGABYTES),
) {
    private companion object {
        const val DEFAULT_MAX_GIGABYTES = 20L
    }
}

class UploadTooLargeException(
    limit: DataSize,
) : RuntimeException("Templates may be at most ${limit.toMegabytes()} MB")

/** Template versions (D-0042): uploads stream into object storage, promotions change the group (D-0103). */
@Service
class Templates(
    private val groups: GroupStore,
    private val templates: TemplateVersionStore,
    private val objects: ObjectStore,
    private val announcements: Announcements,
    private val properties: TemplateProperties,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    private val promotion = TemplatePromotion(groups)

    /** Newest first, with the group so callers can tell the active one. */
    fun list(groupId: GroupId): Pair<Group, List<TemplateVersion>> =
        group(groupId) to templates.findByGroup(groupId).sortedByDescending(TemplateVersion::createdAt)

    /** Streams the archive into storage while hashing it; [declaredLength] is -1 when the client sent none. */
    fun upload(
        actor: Actor,
        groupId: GroupId,
        body: InputStream,
        declaredLength: Long,
    ): TemplateVersion {
        val group = group(groupId)
        val limit = properties.maxUploadSize
        if (declaredLength > limit.toBytes()) throw UploadTooLargeException(limit)
        val digest = MessageDigest.getInstance(SHA_256)
        val counted = BoundedInputStream(DigestInputStream(gzip(body), digest), limit.toBytes())
        val id = ids.next()
        val key = TemplateKeys.of(groupId, id)
        try {
            objects.upload(key, counted)
        } catch (e: Exception) {
            // The storage wraps what the stream threw; the flag tells an oversized upload from a failure.
            if (counted.exceeded) throw UploadTooLargeException(limit)
            throw e
        }
        val version =
            TemplateVersion(
                id,
                groupId,
                key,
                HexFormat.of().formatHex(digest.digest()),
                counted.count,
                TemplateSource.UPLOAD,
                actor,
                clock.instant(),
            )
        templates.add(version)
        // STATIC groups start from every new version; DYNAMIC groups wait for a promote (D-0042).
        if (group.type == GroupType.STATIC) promote(actor, groupId, id)
        return version
    }

    fun download(
        groupId: GroupId,
        versionId: UUID,
        target: OutputStream,
    ) = objects.download(version(groupId, versionId).objectKey, target)

    /** Promotes a version, or rolls back to an older one. */
    fun promote(
        actor: Actor,
        groupId: GroupId,
        versionId: UUID,
    ): TemplateVersion {
        val version = version(groupId, versionId)
        promotion.promote(groupId, versionId)
        announcements.group(actor, groupId)
        return version
    }

    fun delete(
        groupId: GroupId,
        versionId: UUID,
    ) {
        val version = version(groupId, versionId)
        // The store refuses the active version; the object goes only once the row has.
        templates.delete(versionId)
        objects.delete(version.objectKey)
    }

    private fun group(groupId: GroupId): Group =
        groups.find(groupId)?.value ?: throw NoSuchElementException("Group $groupId does not exist")

    private fun version(
        groupId: GroupId,
        versionId: UUID,
    ): TemplateVersion =
        templates.find(versionId)?.takeIf { it.groupId == groupId }
            ?: throw NoSuchElementException("Group $groupId has no template version $versionId")

    /** Refuses anything but gzip before a byte is stored. */
    private fun gzip(body: InputStream): InputStream {
        val stream = PushbackInputStream(body, GZIP_MAGIC.size)
        val head = stream.readNBytes(GZIP_MAGIC.size)
        require(head.contentEquals(GZIP_MAGIC)) { "Templates are .tar.gz archives" }
        stream.unread(head)
        return stream
    }

    private companion object {
        const val SHA_256 = "SHA-256"
        val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())
    }
}

/** Counts what passes and stops at [limit], marking [exceeded], so an oversized upload never completes. */
private class BoundedInputStream(
    source: InputStream,
    private val limit: Long,
) : FilterInputStream(source) {
    var count = 0L
        private set
    var exceeded = false
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) record(read.toLong())
        return read
    }

    private fun record(bytes: Long) {
        count += bytes
        if (count > limit) {
            exceeded = true
            throw IOException("Upload exceeds $limit bytes")
        }
    }
}
