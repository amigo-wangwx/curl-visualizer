package com.wang.curlvisualizer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Formats response bodies when they are valid JSON.
 *
 * Lifecycle: invoked by UI toggles only; it never mutates the original curl body.
 */
object JsonFormatter {
    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Returns a formatted JSON body or null when the body is not valid JSON.
     *
     * JSON strings are unwrapped only by the JSON library so escaped payloads stay valid.
     */
    fun formatOrNull(text: String): String? {
        return runCatching {
            val element = Json.parseToJsonElement(text)
            prettyJson.encodeToString(JsonElement.serializer(), element)
        }.getOrNull()
    }

    /**
     * Detects whether a response body can be parsed as JSON.
     *
     * Blank bodies are intentionally treated as non-JSON to avoid showing misleading controls.
     */
    fun isJson(text: String): Boolean {
        if (text.isBlank()) return false
        return runCatching {
            Json.parseToJsonElement(text)
        }.isSuccess
    }
}
