package io.ogwars.cloud.domain.common

/** What a secret shows in a toString, so settings and request bodies can be logged (AGENTS.md §14). */
const val REDACTED = "<redacted>"

/** [REDACTED] for a secret that is set, null for one that is not. */
fun redacted(secret: String?): String? = secret?.let { REDACTED }
