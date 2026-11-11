package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.ParseResult
import io.ogwars.cloud.text.RichText

/**
 * A text the network stores, such as a group's prefix or the tablist header. The API refuses invalid ones (D-0200),
 * so one that still fails to parse, as from an older release, is shown as written rather than dropped.
 */
fun storedText(source: String): RichText =
    when (val result = RichText.parse(source)) {
        is ParseResult.Valid -> result.text
        is ParseResult.Invalid -> RichText.plain(source)
    }
