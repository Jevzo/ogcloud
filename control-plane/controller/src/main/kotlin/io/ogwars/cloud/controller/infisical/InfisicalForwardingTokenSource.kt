package io.ogwars.cloud.controller.infisical

import io.ogwars.cloud.controller.jobs.ForwardingTokenSource
import io.ogwars.cloud.controller.jobs.forwardingTokens

/** The forwarding tokens in Infisical, one per line; the last one is what proxies use (D-0065). */
class InfisicalForwardingTokenSource(
    private val secret: InfisicalSecret,
) : ForwardingTokenSource {
    override fun tokens(): List<String> = secret.read()?.let(::forwardingTokens).orEmpty()

    override fun add(token: String) {
        val tokens = tokens()
        if (token !in tokens) secret.write((tokens + token).joinToString("\n"))
    }

    override fun remove(token: String) {
        val remaining = tokens() - token
        // Without a token no proxy could reach a backend.
        check(remaining.isNotEmpty()) { "Refusing to remove the last forwarding token" }
        secret.write(remaining.joinToString("\n"))
    }
}
