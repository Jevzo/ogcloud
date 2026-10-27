package io.ogwars.cloud.api.auth

import org.springframework.security.crypto.password.PasswordEncoder

/** [PasswordEncoder.encode] is nullable only to pass a null input through; ours are never null. */
fun PasswordEncoder.hash(raw: String): String = encode(raw) ?: error("The password encoder returned no hash")
