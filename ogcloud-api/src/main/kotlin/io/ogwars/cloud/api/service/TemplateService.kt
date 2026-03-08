package io.ogwars.cloud.api.service

import io.minio.*
import io.minio.errors.ErrorResponseException
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.PaginationSupport
import io.ogwars.cloud.api.exception.TemplateNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class TemplateService(
    private val minioClient: MinioClient,
    @Value("\${ogcloud.minio.bucket}") private val bucket: String,
    private val auditLogService: AuditLogService,
) {
    data class TemplateInfo(
        val group: String,
        val version: String,
        val path: String,
    )

    data class TemplateDownload(
        val inputStream: InputStream,
        val size: Long,
        val fileName: String,
    )

    fun listTemplates(
        group: String?,
        query: String?,
        page: Int,
        size: Int?,
    ): PaginatedResponse<TemplateInfo> {
        val results =
            minioClient.listObjects(
                ListObjectsArgs
                    .builder()
                    .bucket(bucket)
                    .recursive(true)
                    .build(),
            )

        val templates =
            results
                .mapNotNull { result -> result.get().objectName().toTemplateInfoOrNull() }
                .filter { template ->
                    if (group != null && template.group != group) {
                        return@filter false
                    }
                    PaginationSupport.matchesQuery(query, template.group, template.version, template.path)
                }.sortedWith(
                    compareBy<TemplateInfo> { it.group }
                        .thenByDescending { it.version }
                        .thenBy { it.path },
                )

        return PaginationSupport.paginate(templates, page, size)
    }

    fun uploadTemplate(
        group: String,
        version: String,
        inputStream: InputStream,
        size: Long,
    ) {
        val objectName = templateObjectName(group, version)

        minioClient.putObject(
            PutObjectArgs
                .builder()
                .bucket(bucket)
                .`object`(objectName)
                .stream(inputStream, size, -1)
                .contentType(TEMPLATE_CONTENT_TYPE)
                .build(),
        )

        auditLogService.logApiAction(
            action = "TEMPLATE_UPLOADED",
            targetType = "TEMPLATE",
            targetId = objectName,
            summary = "Uploaded template $objectName",
            metadata = mapOf("group" to group, "version" to version),
        )
    }

    fun downloadTemplate(
        group: String,
        version: String,
    ): TemplateDownload {
        val objectName = templateObjectName(group, version)
        val stat = requireTemplate(objectName, group, version)

        val inputStream =
            minioClient.getObject(
                GetObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(objectName)
                    .build(),
            )

        return TemplateDownload(
            inputStream = inputStream,
            size = stat.size(),
            fileName = "$group-$version-template.tar.gz",
        )
    }

    fun deleteTemplate(
        group: String,
        version: String,
    ) {
        val objectName = templateObjectName(group, version)
        requireTemplate(objectName, group, version)

        minioClient.removeObject(
            RemoveObjectArgs
                .builder()
                .bucket(bucket)
                .`object`(objectName)
                .build(),
        )

        auditLogService.logApiAction(
            action = "TEMPLATE_DELETED",
            targetType = "TEMPLATE",
            targetId = objectName,
            summary = "Deleted template $objectName",
            metadata = mapOf("group" to group, "version" to version),
        )
    }

    private fun templateObjectName(
        group: String,
        version: String,
    ): String = "$group/$version/$TEMPLATE_FILE_NAME"

    private fun requireTemplate(
        objectName: String,
        group: String,
        version: String,
    ) = try {
        minioClient.statObject(
            StatObjectArgs
                .builder()
                .bucket(bucket)
                .`object`(objectName)
                .build(),
        )
    } catch (ex: ErrorResponseException) {
        if (ex.errorResponse().code() in NOT_FOUND_CODES) {
            throw TemplateNotFoundException(group, version)
        }
        throw ex
    }

    private fun String.toTemplateInfoOrNull(): TemplateInfo? {
        val parts = split("/")

        if (parts.size < MIN_TEMPLATE_PATH_PARTS || parts.last() != TEMPLATE_FILE_NAME) {
            return null
        }

        return TemplateInfo(
            group = parts[0],
            version = parts[1],
            path = this,
        )
    }

    companion object {
        private const val MIN_TEMPLATE_PATH_PARTS = 3
        private const val TEMPLATE_CONTENT_TYPE = "application/gzip"
        private const val TEMPLATE_FILE_NAME = "template.tar.gz"
        private val NOT_FOUND_CODES = setOf("NoSuchKey", "NoSuchObject", "NoSuchFile")
    }
}
