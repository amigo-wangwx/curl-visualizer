package com.wang.curlvisualizer

import kotlinx.serialization.Serializable

/**
 * Parses curl --include output into headers and body.
 *
 * Lifecycle: called after every curl execution; multiple HTTP blocks are supported for redirects.
 */
object CurlResponseParser {
    /**
     * Splits stdout into the latest HTTP header block and response body.
     *
     * curl may emit several header blocks when redirects happen, so the last block before the body is treated as active.
     */
    fun parse(stdout: String): CurlResponse {
        val normalized = stdout.replace("\r\n", "\n")
        val blocks = normalized.split("\n\n")
        val headerBlocks = blocks.takeWhile { it.startsWith("HTTP/") }
        if (headerBlocks.isEmpty()) {
            return CurlResponse(statusLine = "", headers = emptyList(), body = stdout)
        }

        val activeHeader = headerBlocks.last()
        val body = blocks.drop(headerBlocks.size).joinToString("\n\n")
        val lines = activeHeader.lines().filter { it.isNotBlank() }
        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            HeaderLine(
                name = line.substring(0, separator).trim(),
                value = line.substring(separator + 1).trim(),
            )
        }

        return CurlResponse(
            statusLine = lines.firstOrNull().orEmpty(),
            headers = headers,
            body = body,
        )
    }
}

/**
 * Parsed HTTP response shown by the visualizer.
 *
 * The body remains raw so JSON formatting and search can be toggled without losing original output.
 */
@Serializable
data class CurlResponse(
    val statusLine: String,
    val headers: List<HeaderLine>,
    val body: String,
)

/**
 * One HTTP header name/value pair.
 *
 * Header order is kept from curl output because repeated headers can matter during debugging.
 */
@Serializable
data class HeaderLine(
    val name: String,
    val value: String,
)
