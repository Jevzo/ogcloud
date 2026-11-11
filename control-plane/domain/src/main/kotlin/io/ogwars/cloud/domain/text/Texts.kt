package io.ogwars.cloud.domain.text

import io.ogwars.cloud.text.TextError
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.describe

/** One stored text, the field it is kept in, and its [label] where it is refused. */
data class FieldText(
    val label: String,
    val field: TextField,
    val source: String,
)

/** Texts their fields may not hold (D-0205): each refused text's label with its errors. */
class InvalidTextException(
    val errors: Map<String, List<TextError>>,
) : IllegalArgumentException(errors.entries.joinToString("; ") { (label, found) -> "$label: ${found.describe()}" })

/** Refuses the [texts] their fields may not hold. Writes call it and reads never do, so a stored text always loads. */
fun requireTexts(vararg texts: FieldText) {
    val errors = texts.associate { it.label to it.field.validate(it.source) }.filterValues { it.isNotEmpty() }
    if (errors.isNotEmpty()) throw InvalidTextException(errors)
}
