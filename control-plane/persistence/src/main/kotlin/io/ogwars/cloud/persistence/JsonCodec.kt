package io.ogwars.cloud.persistence

import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/** Reads and writes the string maps stored in jsonb columns. */
@Component
class JsonCodec {
    private val mapper = JsonMapper.builder().build()

    fun write(value: Any): String = mapper.writeValueAsString(value)

    fun readStringMap(json: String): Map<String, String> = mapper.readValue(json, STRING_MAP)

    fun readTree(json: String) = mapper.readTree(json)

    private companion object {
        val STRING_MAP = object : TypeReference<Map<String, String>>() {}
    }
}
