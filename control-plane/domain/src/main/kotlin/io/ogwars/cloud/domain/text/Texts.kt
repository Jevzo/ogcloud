package io.ogwars.cloud.domain.text

import io.ogwars.cloud.text.TextField

/** Refuses an in-game text that [field] may not hold, naming each error and where it is (D-0200). */
internal fun requireText(
    label: String,
    field: TextField,
    source: String,
) {
    val errors = field.validate(source)
    require(errors.isEmpty()) { "$label: " + errors.joinToString("; ") { "${it.reason} (at character ${it.offset})" } }
}
