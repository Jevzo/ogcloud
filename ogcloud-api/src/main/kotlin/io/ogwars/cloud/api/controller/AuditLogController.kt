package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.ApiAuditLogResponse
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.ScalingLogResponse
import io.ogwars.cloud.api.service.AuditLogService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit")
@Validated
class AuditLogController(
    private val auditLogService: AuditLogService
) {

    @GetMapping("/api")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    fun listApiLogs(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(required = false) @Min(1, message = "size must be greater than 0") @Max(200, message = "size must be less than or equal to 200") size: Int?
    ): PaginatedResponse<ApiAuditLogResponse> = auditLogService.listApiLogs(query, page, size)

    @GetMapping("/scaling")
    fun listScalingLogs(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(required = false) @Min(1, message = "size must be greater than 0") @Max(200, message = "size must be less than or equal to 200") size: Int?
    ): PaginatedResponse<ScalingLogResponse> = auditLogService.listScalingLogs(query, page, size)
}
