package com.amigo_wangwx.curlvisualizer.data.history

import com.amigo_wangwx.curlvisualizer.curl.HeaderLine
import kotlinx.serialization.Serializable

/**
 * Root model persisted to the local history file.
 *
 * Lifecycle: loaded once when the app starts, then rewritten after history mutations.
 */
@Serializable
data class HistoryState(
    val requests: List<CurlHistoryItem> = emptyList(),
    val responses: List<ResponseHistoryItem> = emptyList(),
)

/**
 * Request history entry keyed by the normalized curl command.
 *
 * The responseId links to a response history entry when a response body was small enough to persist.
 */
@Serializable
data class CurlHistoryItem(
    val id: String,
    val command: String,
    val executedAtMillis: Long,
    val statusLine: String,
    val exitCode: Int,
    val elapsedMillis: Long,
    val responseId: String?,
)

/**
 * Response history entry keyed by response body content.
 *
 * Duplicate response bodies update timestamps and metadata instead of creating another saved body.
 */
@Serializable
data class ResponseHistoryItem(
    val id: String,
    val body: String,
    val updatedAtMillis: Long,
    val statusLine: String,
    val headers: List<HeaderLine>,
    val exitCode: Int,
)
