package io.ogwars.cloud.domain.common

import java.security.MessageDigest
import java.util.HexFormat

/** Lowercase hex SHA-256 of [text]'s UTF-8 bytes, e.g. to look a secret up without storing it. */
fun sha256Hex(text: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
