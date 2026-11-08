package io.ogwars.cloud.bukkit

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.UUID

/** A login the proxy forwarded: what the server needs to know about the player. */
data class ForwardedLogin(
    val serverHostname: String,
    val address: String,
    val uuid: UUID,
    /** The player's properties, e.g. their skin, without the forwarding token. */
    val propertiesJson: String,
)

/** Whether a handshake came from one of OgCloud's proxies. */
sealed interface HandshakeVerdict {
    data class Accepted(
        val login: ForwardedLogin,
    ) : HandshakeVerdict

    data class Refused(
        val reason: String,
    ) : HandshakeVerdict
}

/**
 * BungeeGuard forwarding (D-0065): the proxy sends `host\0address\0uuid\0properties` and puts its token among the
 * properties. A backend takes the login only with exactly one token it knows, so nothing but OgCloud's proxies can
 * claim to be a player.
 */
object ForwardingHandshake {
    private const val TOKEN_PROPERTY = "bungeeguard-token"
    private const val PARTS = 4

    fun verify(
        handshake: String,
        tokens: Set<String>,
    ): HandshakeVerdict {
        val parts = handshake.split('\u0000')
        if (parts.size != PARTS) return HandshakeVerdict.Refused("not a forwarded login")
        val (host, address, rawUuid, rawProperties) = parts
        val uuid = parseUuid(rawUuid) ?: return HandshakeVerdict.Refused("malformed player id")
        val properties = parseProperties(rawProperties) ?: return HandshakeVerdict.Refused("malformed properties")
        if (properties.any {
                it.asJsonObject.text(
                    "name",
                ) == null
            }
        ) {
            return HandshakeVerdict.Refused("a property has no name")
        }
        val sent = properties.filter { it.asJsonObject.text("name") == TOKEN_PROPERTY }
        if (sent.size != 1) return HandshakeVerdict.Refused("expected one forwarding token, got ${sent.size}")
        if (sent.single().asJsonObject.text("value") !in
            tokens
        ) {
            return HandshakeVerdict.Refused("unknown forwarding token")
        }
        val kept = JsonArray().apply { properties.filterNot { it in sent }.forEach(::add) }
        return HandshakeVerdict.Accepted(ForwardedLogin(host, address, uuid, kept.toString()))
    }

    /** A string member, or null for anything else, e.g. JSON null, which Gson cannot read as a string. */
    private fun JsonObject.text(key: String): String? =
        get(key)
            ?.takeIf {
                it.isJsonPrimitive &&
                    it.asJsonPrimitive.isString
            }?.asString

    /** Forwarding sends the id without dashes. */
    private fun parseUuid(raw: String): UUID? =
        runCatching {
            UUID.fromString(
                raw.replaceFirst(
                    Regex("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)"),
                    "$1-$2-$3-$4-$5",
                ),
            )
        }.getOrNull()

    private fun parseProperties(raw: String): JsonArray? =
        try {
            JsonParser().parse(raw).takeIf { it.isJsonArray }?.asJsonArray?.takeIf { array ->
                array.all { it.isJsonObject }
            }
        } catch (_: JsonParseException) {
            null
        }
}
