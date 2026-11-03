package io.github.donnclab.rct.studio

import kotlinx.serialization.json.*

/** Strict JSON, deliberately separate from the host application's storage or model serialisation. */
internal object StudioJson {
    private val json = Json { prettyPrint = true }
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")

    fun parse(source: String): Map<String, Any?> = objectValue(element(source))

    fun format(source: String): String {
        parse(source)
        return json.encodeToString(JsonElement.serializer(), element(source))
    }

    private fun element(source: String): JsonObject {
        require(source.length <= 262_144) { "Sample data must be at most 262,144 characters." }
        // Bound nesting before invoking a recursive parser, including while the draft is invalid.
        var depth = 0
        var quoted = false
        var escaped = false
        source.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "Sample data nesting exceeds 64 levels." } }
                '}', ']' -> depth--
            }
        }
        // Tree parsers may accept non-standard primitive spellings. Check their raw tokens too.
        var index = 0
        while (index < source.length) {
            when {
                source[index] == '"' -> {
                    index++
                    while (index < source.length && source[index] != '"') {
                        require(source[index].code >= 32) { "Escape control characters inside JSON strings." }
                        if (source[index] == '\\') index++
                        index++
                    }
                    if (index < source.length) index++
                }
                source[index].isWhitespace() || source[index] in "{}[]:," -> index++
                else -> {
                    val start = index
                    while (index < source.length && !source[index].isWhitespace() && source[index] !in "{}[]:,") index++
                    val token = source.substring(start, index)
                    require(token == "true" || token == "false" || token == "null" || number.matches(token)) { "Invalid JSON literal '$token' at character ${start + 1}." }
                }
            }
        }
        return json.parseToJsonElement(source) as? JsonObject
            ?: throw IllegalArgumentException("Sample data must be a JSON object: { ... }")
    }

    private fun objectValue(value: JsonObject): Map<String, Any?> = value.mapValues { convert(it.value) }

    private fun convert(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> objectValue(value)
        is JsonArray -> value.map(::convert)
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.content == "true" || value.content == "false" -> value.boolean
            else -> {
                require(number.matches(value.content)) { "Invalid JSON value: ${value.content}" }
                value.content.toLongOrNull() ?: value.content.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("Invalid JSON number: ${value.content}")
            }
        }
    }
}
