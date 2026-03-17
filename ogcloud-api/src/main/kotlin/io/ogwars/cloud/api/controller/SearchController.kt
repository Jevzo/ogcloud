package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.SearchResponse
import io.ogwars.cloud.api.service.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Search")
@SecurityRequirement(name = "bearerAuth")
class SearchController(
    private val searchService: SearchService,
) {
    @GetMapping("/{query}")
    @Operation(summary = "Search across supported network resources")
    fun search(
        @PathVariable @NotBlank(message = "query must not be blank") query: String,
        @RequestParam(
            required = false,
        ) @Min(
            1,
            message = "limit must be greater than 0",
        ) @Max(50, message = "limit must be less than or equal to 50") limit: Int?,
    ): SearchResponse = searchService.search(query, limit)
}
