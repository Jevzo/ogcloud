package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.service.TemplateService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/templates")
@Validated
class TemplateController(
    private val templateService: TemplateService,
) {
    @GetMapping
    fun listTemplates(
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(
            required = false,
        ) @Min(
            1,
            message = "size must be greater than 0",
        ) @Max(200, message = "size must be less than or equal to 200") size: Int?,
    ): PaginatedResponse<TemplateService.TemplateInfo> = templateService.listTemplates(group, query, page, size)

    @PostMapping("/{group}/upload")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadTemplate(
        @PathVariable group: String,
        @RequestParam version: String,
        @RequestParam file: MultipartFile,
    ) = templateService.uploadTemplate(group, version, file.inputStream, file.size)

    @GetMapping("/{group}/{version}/download")
    fun downloadTemplate(
        @PathVariable group: String,
        @PathVariable version: String,
    ): ResponseEntity<InputStreamResource> {
        val download = templateService.downloadTemplate(group, version)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/gzip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${download.fileName}\"")
            .contentLength(download.size)
            .body(InputStreamResource(download.inputStream))
    }

    @DeleteMapping("/{group}/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTemplate(
        @PathVariable group: String,
        @PathVariable version: String,
    ) = templateService.deleteTemplate(group, version)
}
