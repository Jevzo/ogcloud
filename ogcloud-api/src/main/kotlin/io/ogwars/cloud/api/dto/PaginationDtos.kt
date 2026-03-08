package io.ogwars.cloud.api.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import java.util.regex.Pattern

data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
)

object PaginationSupport {
    private const val DEFAULT_PAGE_SIZE = 50
    private const val MAX_PAGE_SIZE = 200

    fun matchesQuery(
        query: String?,
        vararg values: String?,
    ): Boolean {
        val normalizedQuery = normalizeQuery(query)?.lowercase() ?: return true

        return values.any { value ->
            value?.lowercase()?.contains(normalizedQuery) == true
        }
    }

    fun resolvePageSize(size: Int?): Int = (size ?: DEFAULT_PAGE_SIZE).coerceAtMost(MAX_PAGE_SIZE)

    fun toPageRequest(
        page: Int,
        size: Int?,
    ): PageRequest = PageRequest.of(page, resolvePageSize(size))

    fun <T> toResponse(
        items: List<T>,
        page: Int,
        size: Int,
        totalItems: Long,
    ): PaginatedResponse<T> =
        PaginatedResponse(
            items = items,
            page = page,
            size = size,
            totalItems = totalItems,
        )

    fun <T> paginate(
        items: List<T>,
        page: Int,
        size: Int?,
    ): PaginatedResponse<T> {
        val resolvedSize = resolvePageSize(size)
        val totalItems = items.size
        val safeOffset = calculateSafeOffset(page, resolvedSize, totalItems)
        val endExclusive = (safeOffset + resolvedSize).coerceAtMost(totalItems)

        return toResponse(
            items = items.subList(safeOffset, endExclusive),
            page = page,
            size = resolvedSize,
            totalItems = totalItems.toLong(),
        )
    }

    fun buildSearchCriteria(
        query: String?,
        vararg fields: String,
    ): Criteria? {
        val normalizedQuery = normalizeQuery(query) ?: return null
        return Criteria().orOperator(
            *fields.map { field -> Criteria.where(field).regex(Pattern.quote(normalizedQuery), "i") }.toTypedArray(),
        )
    }

    private fun normalizeQuery(query: String?): String? = query?.trim()?.takeUnless(String::isEmpty)

    private fun calculateSafeOffset(
        page: Int,
        resolvedSize: Int,
        totalItems: Int,
    ): Int {
        val rawOffset = page.toLong() * resolvedSize.toLong()

        if (rawOffset >= totalItems) {
            return totalItems
        }

        return rawOffset.toInt()
    }
}
