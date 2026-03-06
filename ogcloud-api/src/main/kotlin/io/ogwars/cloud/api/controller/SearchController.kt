package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.SearchResponse
import io.ogwars.cloud.api.service.SearchService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@RestController
@RequestMapping("/api/v1/search")
@Validated
class SearchController(
    private val searchService: SearchService
) {

    @GetMapping("/{query}")
    fun search(
        @PathVariable @NotBlank(message = "query must not be blank") query: String,
        @RequestParam(required = false) @Min(1, message = "limit must be greater than 0") @Max(50, message = "limit must be less than or equal to 50") limit: Int?
    ): SearchResponse = searchService.search(query, limit)
}
